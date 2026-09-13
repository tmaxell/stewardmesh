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

class ActionPlanApprovalPolicyTest {

    private static final Instant NOW = Instant.parse("2026-09-12T12:00:00Z");
    private final ActionPlanApprovalPolicy policy = new ActionPlanApprovalPolicy();

    @Test
    void bindsAnAuthorizedIndependentHumanDecisionToTheExactPlan() {
        GovernedActionPlan plan = proposed();

        ActionPlanApproval approval = policy.decide(
                plan, plan.version(), plan.hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey("decision-17"), "human-approver", true, NOW,
                "Evidence and simulation reviewed");

        assertEquals(plan.id(), approval.planId());
        assertEquals(plan.version(), approval.planVersion());
        assertEquals(plan.hash(), approval.planHash());
        assertEquals(ActionPlanStatus.APPROVED, approval.resultingStatus());
        assertEquals("human-approver", approval.decidedBySubject());
    }

    @Test
    void mapsARejectionToTheTerminalRejectedState() {
        GovernedActionPlan plan = proposed();

        ActionPlanApproval approval = policy.decide(
                plan, plan.version(), plan.hash(), ApprovalDecision.REJECT,
                new ApprovalRequestKey("decision-18"), "human-approver", true, NOW,
                "Required evidence is incomplete");

        assertEquals(ActionPlanStatus.REJECTED, approval.resultingStatus());
    }

    @Test
    void rejectsStaleOrTamperedBinding() {
        GovernedActionPlan plan = proposed();

        assertViolation(ApprovalPolicyCode.PLAN_BINDING_MISMATCH, () -> policy.decide(
                plan, new ActionPlanVersion(2), plan.hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey("decision-19"), "human-approver", true, NOW, "Reviewed"));
        assertViolation(ApprovalPolicyCode.PLAN_BINDING_MISMATCH, () -> policy.decide(
                plan, plan.version(), new ActionPlanHash("0".repeat(64)), ApprovalDecision.APPROVE,
                new ApprovalRequestKey("decision-20"), "human-approver", true, NOW, "Reviewed"));
    }

    @Test
    void enforcesAuthorizationAndSeparationOfDuties() {
        GovernedActionPlan plan = proposed();

        assertViolation(ApprovalPolicyCode.APPROVAL_NOT_AUTHORIZED, () -> policy.decide(
                plan, plan.version(), plan.hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey("decision-21"), "human-viewer", false, NOW, "Reviewed"));
        assertViolation(ApprovalPolicyCode.SELF_APPROVAL_FORBIDDEN, () -> policy.decide(
                plan, plan.version(), plan.hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey("decision-22"), "agent-proposer", true, NOW, "Reviewed"));
    }

    @Test
    void refusesASecondDecisionOrInvalidDecisionMetadata() {
        GovernedActionPlan approved = proposed().transitionTo(ActionPlanStatus.APPROVED);

        assertViolation(ApprovalPolicyCode.PLAN_NOT_PROPOSED, () -> policy.decide(
                approved, approved.version(), approved.hash(), ApprovalDecision.REJECT,
                new ApprovalRequestKey("decision-23"), "human-approver", true, NOW, "Changed mind"));
        assertThrows(IllegalArgumentException.class, () -> new ApprovalRequestKey(" "));
        assertThrows(IllegalArgumentException.class, () -> policy.decide(
                proposed(), proposed().version(), proposed().hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey("decision-24"), "human-approver", true, NOW, " "));
    }

    private static void assertViolation(ApprovalPolicyCode expected, Runnable operation) {
        ApprovalPolicyViolationException failure =
                assertThrows(ApprovalPolicyViolationException.class, operation::run);
        assertEquals(expected, failure.code());
    }

    private static GovernedActionPlan proposed() {
        SourceRecordIdentity source = new SourceRecordIdentity(
                new SourceSystemRef("synthetic-erp"), "supplier-42", 7);
        ActionPlan plan = ActionPlan.propose(
                new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000701")),
                ActionPlanVersion.initial(),
                new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000702")),
                Instant.parse("2026-09-12T11:00:00Z"),
                "agent-proposer",
                List.of(new CreateSupplierPartyStep(
                        1,
                        new SupplierPartyId(
                                UUID.fromString("00000000-0000-0000-0000-000000000703")),
                        source,
                        new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(new EvidenceReference(
                                EvidenceType.SOURCE_RECORD, "synthetic-erp:supplier-42", 7)))));
        return GovernedActionPlan.proposed(plan);
    }
}
