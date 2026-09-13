package io.stewardmesh.masterdata.domain.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GovernedActionPlanTest {

    private static final ImportJobId IMPORT_ID =
            new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000201"));
    private static final Instant CREATED_AT = Instant.parse("2026-09-12T08:15:00Z");

    @Test
    void startsProposedAndExposesTheSealedPlanIdentity() {
        ActionPlan plan = plan("00000000-0000-0000-0000-000000000202", CREATED_AT, "steward-1");

        GovernedActionPlan governed = GovernedActionPlan.proposed(plan);

        assertEquals(ActionPlanStatus.PROPOSED, governed.status());
        assertSame(plan, governed.plan());
        assertEquals(plan.id(), governed.id());
        assertEquals(plan.version(), governed.version());
        assertEquals(plan.hash(), governed.hash());
        assertEquals(plan.fingerprint(), governed.fingerprint());
        assertEquals(plan.risk(), governed.risk());
    }

    @Test
    void advancesThroughApprovalAndExecutionWithoutChangingThePlan() {
        GovernedActionPlan proposed = GovernedActionPlan.proposed(
                plan("00000000-0000-0000-0000-000000000203", CREATED_AT, "steward-1"));

        GovernedActionPlan executed = proposed
                .transitionTo(ActionPlanStatus.APPROVED)
                .transitionTo(ActionPlanStatus.EXECUTING)
                .transitionTo(ActionPlanStatus.EXECUTED);

        assertEquals(ActionPlanStatus.EXECUTED, executed.status());
        assertEquals(proposed.plan(), executed.plan());
        assertEquals(proposed.hash(), executed.hash());
        assertEquals(ActionPlanStatus.PROPOSED, proposed.status());
    }

    @Test
    void refusesTransitionsOutsideTheGovernedLifecycle() {
        GovernedActionPlan proposed = GovernedActionPlan.proposed(
                plan("00000000-0000-0000-0000-000000000204", CREATED_AT, "steward-1"));

        assertThrows(
                InvalidActionPlanTransitionException.class,
                () -> proposed.transitionTo(ActionPlanStatus.EXECUTING));
        assertThrows(
                InvalidActionPlanTransitionException.class,
                () -> proposed.transitionTo(ActionPlanStatus.EXECUTED));

        GovernedActionPlan rejected = proposed.transitionTo(ActionPlanStatus.REJECTED);
        assertThrows(
                InvalidActionPlanTransitionException.class,
                () -> rejected.transitionTo(ActionPlanStatus.APPROVED));
        assertThrows(NullPointerException.class, () -> rejected.transitionTo(null));
    }

    @Test
    void fingerprintIgnoresIdentityTimeAndProposerButHashDoesNot() {
        ActionPlan first = plan("00000000-0000-0000-0000-000000000205", CREATED_AT, "steward-1");
        ActionPlan second = plan(
                "00000000-0000-0000-0000-000000000206", CREATED_AT.plusSeconds(60), "steward-2");

        assertEquals(first.fingerprint(), second.fingerprint());
        assertNotEquals(first.hash(), second.hash());
    }

    @Test
    void fingerprintSeparatesDifferentImportsAndDifferentSteps() {
        ActionPlan baseline = plan("00000000-0000-0000-0000-000000000207", CREATED_AT, "steward-1");

        ActionPlan otherImport = ActionPlan.propose(
                new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000208")),
                ActionPlanVersion.initial(),
                new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000209")),
                CREATED_AT,
                "steward-1",
                steps(7));
        ActionPlan otherSteps = ActionPlan.propose(
                new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000210")),
                ActionPlanVersion.initial(),
                IMPORT_ID,
                CREATED_AT,
                "steward-1",
                steps(8));

        assertNotEquals(baseline.fingerprint(), otherImport.fingerprint());
        assertNotEquals(baseline.fingerprint(), otherSteps.fingerprint());
        assertThrows(IllegalArgumentException.class, () -> new ActionPlanFingerprint("not-a-hash"));
    }

    private static ActionPlan plan(String planId, Instant createdAt, String subject) {
        return ActionPlan.propose(
                new ActionPlanId(UUID.fromString(planId)),
                ActionPlanVersion.initial(),
                IMPORT_ID,
                createdAt,
                subject,
                steps(7));
    }

    private static List<ActionPlanStep> steps(long sourceVersion) {
        SourceRecordIdentity source =
                new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-42", sourceVersion);
        return List.of(new CreateSupplierPartyStep(
                1,
                new SupplierPartyId(UUID.fromString("00000000-0000-0000-0000-000000000211")),
                source,
                new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                List.of(new EvidenceReference(
                        EvidenceType.SOURCE_RECORD, "erp-a:supplier-42", sourceVersion))));
    }
}
