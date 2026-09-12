package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.application.port.in.DecideActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanApprovalRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApprovalPolicy;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Records one exact human decision and advances the governed plan in the same transaction. */
public final class ActionPlanApprovalService implements DecideActionPlan {

    private final ActionPlanRepository plans;
    private final ActionPlanApprovalRepository approvals;
    private final ApplicationTransaction transaction;
    private final ActionPlanApprovalPolicy policy;
    private final Clock clock;

    public ActionPlanApprovalService(
            ActionPlanRepository plans,
            ActionPlanApprovalRepository approvals,
            ApplicationTransaction transaction,
            ActionPlanApprovalPolicy policy,
            Clock clock) {
        this.plans = Objects.requireNonNull(plans, "plans must not be null");
        this.approvals = Objects.requireNonNull(approvals, "approvals must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ActionPlanApproval decide(
            DecideActionPlanCommand command, AuthenticatedApprovalActor actor) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(actor, "authenticated actor must not be null");
        return transaction.execute(() -> decideAtomically(command, actor));
    }

    private ActionPlanApproval decideAtomically(
            DecideActionPlanCommand command, AuthenticatedApprovalActor actor) {
        var repeated = approvals.findBySubjectAndRequestKey(actor.subject(), command.idempotencyKey());
        if (repeated.isPresent()) {
            return requireSameDecision(repeated.orElseThrow(), command);
        }

        GovernedActionPlan governed =
                plans.findById(command.planId()).orElseThrow(ActionPlanNotFoundException::new);
        ActionPlanApproval approval = policy.decide(
                governed,
                command.expectedVersion(),
                command.expectedHash(),
                command.decision(),
                command.idempotencyKey(),
                actor.subject(),
                actor.authorizedToApprove(),
                clock.instant().truncatedTo(ChronoUnit.MICROS),
                command.reason());
        approvals.save(approval);
        plans.save(governed.transitionTo(approval.resultingStatus()));
        return approval;
    }

    private static ActionPlanApproval requireSameDecision(
            ActionPlanApproval stored, DecideActionPlanCommand command) {
        if (!stored.planId().equals(command.planId())
                || !stored.planVersion().equals(command.expectedVersion())
                || !stored.planHash().equals(command.expectedHash())
                || stored.decision() != command.decision()
                || !stored.reason().equals(command.reason().strip())) {
            throw new ApprovalConflictException(
                    "approval idempotency key already identifies a different decision");
        }
        return stored;
    }
}
