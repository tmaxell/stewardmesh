package io.stewardmesh.masterdata.application.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.audit.AuditEvent;
import io.stewardmesh.masterdata.application.messaging.OutboxEvent;
import io.stewardmesh.masterdata.application.port.in.SimulateActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanApprovalRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanExecutionRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanExecutionPolicy;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanFingerprint;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulation;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.ActionType;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalDecision;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalRequestKey;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.ExecutionPolicyViolationException;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.SimulatedStep;
import io.stewardmesh.masterdata.domain.actionplan.SimulationOutcome;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ActionPlanExecutionServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T16:00:00.123456Z");

    @Test
    void appliesEffectsAndCommitsReceiptAuditOutboxAndLifecycle() {
        Fixture fixture = new Fixture();

        ActionPlanExecution result = fixture.service.execute(command("exec-1"),
                new AuthenticatedExecutionActor("human-executor", true));

        assertEquals(ActionPlanStatus.EXECUTED, fixture.plans.current.status());
        assertEquals(1, fixture.applied.size());
        assertEquals(1, fixture.audits.size());
        assertEquals(1, fixture.outbox.size());
        assertEquals(result.correlationId(), fixture.audits.getFirst().correlationId());
        assertEquals(result.correlationId(), fixture.outbox.getFirst().correlationId());
        assertEquals("STEWARDMESH", fixture.outbox.getFirst().originSystem());
    }

    @Test
    void returnsOriginalReceiptForAnIdenticalReplay() {
        Fixture fixture = new Fixture();
        ActionPlanExecution first = fixture.service.execute(command("exec-2"),
                new AuthenticatedExecutionActor("human-executor", true));

        ActionPlanExecution replay = fixture.service.execute(command("exec-2"),
                new AuthenticatedExecutionActor("human-executor", true));

        assertSame(first, replay);
        assertEquals(1, fixture.applied.size());
        assertEquals(1, fixture.outbox.size());
    }

    @Test
    void rejectsConflictingKeyAndUnauthorizedExecutionBeforeEffects() {
        Fixture fixture = new Fixture();
        fixture.service.execute(command("exec-3"),
                new AuthenticatedExecutionActor("human-executor", true));
        ExecuteActionPlanCommand conflict = new ExecuteActionPlanCommand(
                fixture.plan.id(), fixture.plan.version(), fixture.plan.hash(),
                new ExecutionRequestKey("exec-3"), "Different reason");
        assertThrows(ExecutionConflictException.class, () -> fixture.service.execute(
                conflict, new AuthenticatedExecutionActor("human-executor", true)));

        Fixture unauthorized = new Fixture();
        assertThrows(ExecutionPolicyViolationException.class, () -> unauthorized.service.execute(
                command("exec-4"), new AuthenticatedExecutionActor("viewer", false)));
        assertEquals(0, unauthorized.applied.size());
        assertEquals(ActionPlanStatus.APPROVED, unauthorized.plans.current.status());
    }

    @Test
    void propagatesStepFailureSoTheTransactionCanRollEverythingBack() {
        Fixture fixture = new Fixture();
        fixture.failApply = true;

        assertThrows(IllegalStateException.class, () -> fixture.service.execute(
                command("exec-5"), new AuthenticatedExecutionActor("human-executor", true)));

        assertEquals(0, fixture.executions.values.size());
        assertEquals(0, fixture.audits.size());
        assertEquals(0, fixture.outbox.size());
    }

    private static ExecuteActionPlanCommand command(String key) {
        GovernedActionPlan plan = approvedPlan();
        return new ExecuteActionPlanCommand(
                plan.id(), plan.version(), plan.hash(), new ExecutionRequestKey(key),
                "Execute reviewed synthetic onboarding");
    }

    private static GovernedActionPlan approvedPlan() {
        SourceRecordIdentity source = new SourceRecordIdentity(
                new SourceSystemRef("synthetic-erp"), "supplier-execution", 1);
        ActionPlan plan = ActionPlan.propose(
                new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000901")),
                ActionPlanVersion.initial(),
                new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000902")),
                Instant.parse("2026-09-12T15:00:00Z"), "agent-proposer",
                List.of(new CreateSupplierPartyStep(
                        1,
                        new SupplierPartyId(
                                UUID.fromString("00000000-0000-0000-0000-000000000903")),
                        source, new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(new EvidenceReference(
                                EvidenceType.SOURCE_RECORD,
                                "synthetic-erp:supplier-execution", 1)))));
        return GovernedActionPlan.proposed(plan).transitionTo(ActionPlanStatus.APPROVED);
    }

    private static final class Fixture {
        private final GovernedActionPlan plan = approvedPlan();
        private final Plans plans = new Plans(plan);
        private final Approvals approvals = new Approvals(approval(plan));
        private final Executions executions = new Executions();
        private final List<AppliedActionStep> applied = new ArrayList<>();
        private final List<AuditEvent> audits = new ArrayList<>();
        private final List<OutboxEvent> outbox = new ArrayList<>();
        private boolean failApply;
        private final ActionPlanExecutionService service;

        private Fixture() {
            AtomicLong ids = new AtomicLong(950);
            SimulateActionPlan simulator = command -> new ActionPlanSimulation(
                    command.planId(), command.expectedVersion(), command.expectedHash(),
                    SimulationOutcome.EXECUTABLE,
                    List.of(new SimulatedStep(1, ActionType.CREATE_SUPPLIER_PARTY, List.of())));
            service = new ActionPlanExecutionService(
                    plans, approvals, executions, simulator, step -> {
                        if (failApply) {
                            throw new IllegalStateException("synthetic write failure");
                        }
                        AppliedActionStep result = new AppliedActionStep(
                                step.sequence(), step.type(), "SUPPLIER_PARTY",
                                ((CreateSupplierPartyStep) step).partyId().value(), 1,
                                "SupplierCreated");
                        applied.add(result);
                        return result;
                    }, audits::add, outbox::add, new DirectTransaction(),
                    () -> new UUID(0, ids.incrementAndGet()), new ActionPlanExecutionPolicy(),
                    Clock.fixed(NOW, ZoneOffset.UTC));
        }
    }

    private static ActionPlanApproval approval(GovernedActionPlan plan) {
        return new ActionPlanApproval(
                plan.id(), plan.version(), plan.hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey("approval-execution"), "human-approver",
                NOW.minusSeconds(60), "Reviewed");
    }

    private static final class Plans implements ActionPlanRepository {
        private GovernedActionPlan current;
        private Plans(GovernedActionPlan current) { this.current = current; }
        @Override public Optional<GovernedActionPlan> findById(ActionPlanId id) {
            return current.id().equals(id) ? Optional.of(current) : Optional.empty();
        }
        @Override public Optional<GovernedActionPlan> findProposedByFingerprint(ActionPlanFingerprint f) {
            return Optional.empty();
        }
        @Override public GovernedActionPlan save(GovernedActionPlan value) {
            current = value;
            return value;
        }
    }

    private record Approvals(ActionPlanApproval value) implements ActionPlanApprovalRepository {
        @Override public Optional<ActionPlanApproval> findByPlanId(ActionPlanId id) {
            return value.planId().equals(id) ? Optional.of(value) : Optional.empty();
        }
        @Override public Optional<ActionPlanApproval> findBySubjectAndRequestKey(
                String subject, ApprovalRequestKey key) { return Optional.empty(); }
        @Override public ActionPlanApproval save(ActionPlanApproval approval) { return approval; }
    }

    private static final class Executions implements ActionPlanExecutionRepository {
        private final List<ActionPlanExecution> values = new ArrayList<>();
        @Override public Optional<ActionPlanExecution> findBySubjectAndRequestKey(
                String subject, ExecutionRequestKey key) {
            return values.stream().filter(value -> value.executedBySubject().equals(subject)
                    && value.requestKey().equals(key)).findFirst();
        }
        @Override public ActionPlanExecution save(ActionPlanExecution execution) {
            values.add(execution);
            return execution;
        }
    }

    private static final class DirectTransaction implements ApplicationTransaction {
        @Override public <T> T execute(Supplier<T> operation) { return operation.get(); }
    }
}
