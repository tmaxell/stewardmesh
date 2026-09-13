package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.application.audit.AuditEvent;
import io.stewardmesh.masterdata.application.messaging.OutboxEvent;
import io.stewardmesh.masterdata.application.port.in.ExecuteActionPlan;
import io.stewardmesh.masterdata.application.port.in.SimulateActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanApprovalRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanExecutionRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanStepApplier;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.AuditEventRepository;
import io.stewardmesh.masterdata.application.port.out.ExecutionIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.OutboxEventRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanExecutionPolicy;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.ExecutionPolicyCode;
import io.stewardmesh.masterdata.domain.actionplan.ExecutionPolicyViolationException;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Revalidates and applies an approved plan exactly once. Master effects, receipt, audit, outbox and
 * final lifecycle state share one local database transaction.
 */
public final class ActionPlanExecutionService implements ExecuteActionPlan {

    private final ActionPlanRepository plans;
    private final ActionPlanApprovalRepository approvals;
    private final ActionPlanExecutionRepository executions;
    private final SimulateActionPlan simulations;
    private final ActionPlanStepApplier stepApplier;
    private final AuditEventRepository auditEvents;
    private final OutboxEventRepository outboxEvents;
    private final ApplicationTransaction transaction;
    private final ExecutionIdentityGenerator identities;
    private final ActionPlanExecutionPolicy policy;
    private final Clock clock;

    public ActionPlanExecutionService(
            ActionPlanRepository plans,
            ActionPlanApprovalRepository approvals,
            ActionPlanExecutionRepository executions,
            SimulateActionPlan simulations,
            ActionPlanStepApplier stepApplier,
            AuditEventRepository auditEvents,
            OutboxEventRepository outboxEvents,
            ApplicationTransaction transaction,
            ExecutionIdentityGenerator identities,
            ActionPlanExecutionPolicy policy,
            Clock clock) {
        this.plans = Objects.requireNonNull(plans, "plans must not be null");
        this.approvals = Objects.requireNonNull(approvals, "approvals must not be null");
        this.executions = Objects.requireNonNull(executions, "executions must not be null");
        this.simulations = Objects.requireNonNull(simulations, "simulations must not be null");
        this.stepApplier = Objects.requireNonNull(stepApplier, "step applier must not be null");
        this.auditEvents = Objects.requireNonNull(auditEvents, "audit events must not be null");
        this.outboxEvents = Objects.requireNonNull(outboxEvents, "outbox events must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.identities = Objects.requireNonNull(identities, "identities must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ActionPlanExecution execute(
            ExecuteActionPlanCommand command, AuthenticatedExecutionActor actor) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(actor, "authenticated actor must not be null");
        return transaction.execute(() -> executeAtomically(command, actor));
    }

    private ActionPlanExecution executeAtomically(
            ExecuteActionPlanCommand command, AuthenticatedExecutionActor actor) {
        var repeated = executions.findBySubjectAndRequestKey(
                actor.subject(), command.idempotencyKey());
        if (repeated.isPresent()) {
            return requireSameExecution(repeated.orElseThrow(), command);
        }

        GovernedActionPlan governed =
                plans.findById(command.planId()).orElseThrow(ActionPlanNotFoundException::new);
        var approval = approvals.findByPlanId(governed.id()).orElseThrow(() ->
                new ExecutionPolicyViolationException(
                        ExecutionPolicyCode.APPROVAL_MISSING_OR_STALE,
                        "execution requires a persisted exact approval"));
        var simulation = simulations.execute(new SimulateActionPlanCommand(
                governed.id(), command.expectedVersion(), command.expectedHash()));
        policy.authorize(
                governed, approval, command.expectedVersion(), command.expectedHash(),
                actor.authorizedToExecute(), simulation);

        plans.save(governed.transitionTo(ActionPlanStatus.EXECUTING));
        List<AppliedActionStep> effects = governed.plan().steps().stream()
                .map(stepApplier::apply)
                .toList();
        var executedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        UUID correlationId = identities.nextId();
        ActionPlanExecution execution = new ActionPlanExecution(
                identities.nextId(), governed.id(), governed.version(), governed.hash(),
                command.idempotencyKey(), actor.subject(), command.reason(), executedAt,
                correlationId, effects);
        executions.save(execution);
        auditEvents.append(audit(execution));
        effects.forEach(effect -> outboxEvents.append(outbox(execution, effect)));
        plans.save(governed.transitionTo(ActionPlanStatus.EXECUTING)
                .transitionTo(ActionPlanStatus.EXECUTED));
        return execution;
    }

    private AuditEvent audit(ActionPlanExecution execution) {
        return new AuditEvent(
                identities.nextId(), execution.planId(), execution.planVersion(),
                execution.planHash(), execution.executedBySubject(), "ACTION_PLAN_EXECUTE",
                "SUCCEEDED", execution.reason(), execution.executedAt(),
                execution.correlationId(),
                execution.effects().stream().map(AppliedActionStep::subjectId).distinct().toList());
    }

    private OutboxEvent outbox(ActionPlanExecution execution, AppliedActionStep effect) {
        return new OutboxEvent(
                identities.nextId(), effect.eventType(), 1, effect.subjectType(),
                effect.subjectId(), effect.subjectVersion(), "STEWARDMESH",
                "master-data-service", execution.executedAt(), execution.correlationId(),
                execution.planId().value(), Map.of(
                        "planId", execution.planId().value().toString(),
                        "planVersion", Long.toString(execution.planVersion().value()),
                        "stepSequence", Integer.toString(effect.sequence())));
    }

    private static ActionPlanExecution requireSameExecution(
            ActionPlanExecution stored, ExecuteActionPlanCommand command) {
        if (!stored.planId().equals(command.planId())
                || !stored.planVersion().equals(command.expectedVersion())
                || !stored.planHash().equals(command.expectedHash())
                || !stored.reason().equals(command.reason())) {
            throw new ExecutionConflictException(
                    "execution idempotency key already identifies a different command");
        }
        return stored;
    }
}
