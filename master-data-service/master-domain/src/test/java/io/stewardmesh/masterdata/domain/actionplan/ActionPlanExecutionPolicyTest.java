package io.stewardmesh.masterdata.domain.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionPlanExecutionPolicyTest {

    private final ActionPlanExecutionPolicy policy = new ActionPlanExecutionPolicy();

    @Test
    void authorizesExactApprovedExecutablePlan() {
        GovernedActionPlan approved = approved();

        policy.authorize(
                approved, approval(approved), approved.version(), approved.hash(), true,
                executable(approved));
    }

    @Test
    void refusesWrongLifecycleOrBinding() {
        GovernedActionPlan proposed = proposed();
        assertViolation(ExecutionPolicyCode.PLAN_NOT_APPROVED, () -> policy.authorize(
                proposed, approval(proposed), proposed.version(), proposed.hash(), true,
                executable(proposed)));

        GovernedActionPlan approved = approved();
        assertViolation(ExecutionPolicyCode.PLAN_BINDING_MISMATCH, () -> policy.authorize(
                approved, approval(approved), new ActionPlanVersion(2), approved.hash(), true,
                executable(approved)));
    }

    @Test
    void refusesMissingStaleOrRejectedApproval() {
        GovernedActionPlan approved = approved();
        ActionPlanApproval rejected = new ActionPlanApproval(
                approved.id(), approved.version(), approved.hash(), ApprovalDecision.REJECT,
                new ApprovalRequestKey("decision-rejected"), "human", Instant.now(), "Rejected");
        assertViolation(ExecutionPolicyCode.APPROVAL_MISSING_OR_STALE, () -> policy.authorize(
                approved, rejected, approved.version(), approved.hash(), true, executable(approved)));
    }

    @Test
    void refusesUnauthorizedExecutorAndFailedPreconditions() {
        GovernedActionPlan approved = approved();
        assertViolation(ExecutionPolicyCode.EXECUTION_NOT_AUTHORIZED, () -> policy.authorize(
                approved, approval(approved), approved.version(), approved.hash(), false,
                executable(approved)));

        ActionPlanSimulation failed = new ActionPlanSimulation(
                approved.id(), approved.version(), approved.hash(), SimulationOutcome.BLOCKED,
                List.of(new SimulatedStep(
                        1, ActionType.CREATE_SUPPLIER_PARTY,
                        List.of(PreconditionCode.SOURCE_RECORD_NOT_FOUND))));
        assertViolation(ExecutionPolicyCode.PRECONDITION_FAILED, () -> policy.authorize(
                approved, approval(approved), approved.version(), approved.hash(), true, failed));
    }

    private static void assertViolation(ExecutionPolicyCode code, Runnable operation) {
        ExecutionPolicyViolationException failure =
                assertThrows(ExecutionPolicyViolationException.class, operation::run);
        assertEquals(code, failure.code());
    }

    private static GovernedActionPlan approved() {
        return proposed().transitionTo(ActionPlanStatus.APPROVED);
    }

    private static ActionPlanApproval approval(GovernedActionPlan plan) {
        return new ActionPlanApproval(
                plan.id(), plan.version(), plan.hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey("decision-approved"), "human", Instant.now(), "Reviewed");
    }

    private static ActionPlanSimulation executable(GovernedActionPlan plan) {
        return new ActionPlanSimulation(
                plan.id(), plan.version(), plan.hash(), SimulationOutcome.EXECUTABLE,
                List.of(new SimulatedStep(1, ActionType.CREATE_SUPPLIER_PARTY, List.of())));
    }

    private static GovernedActionPlan proposed() {
        SourceRecordIdentity source = new SourceRecordIdentity(
                new SourceSystemRef("synthetic-erp"), "supplier-42", 1);
        ActionPlan plan = ActionPlan.propose(
                new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000801")),
                ActionPlanVersion.initial(),
                new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000802")),
                Instant.parse("2026-09-12T15:00:00Z"), "agent-proposer",
                List.of(new CreateSupplierPartyStep(
                        1,
                        new SupplierPartyId(
                                UUID.fromString("00000000-0000-0000-0000-000000000803")),
                        source, new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(new EvidenceReference(
                                EvidenceType.SOURCE_RECORD, "synthetic-erp:supplier-42", 1)))));
        return GovernedActionPlan.proposed(plan);
    }
}
