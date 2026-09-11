package io.stewardmesh.masterdata.domain.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionPlanTest {

    private static final ActionPlanId PLAN_ID =
            new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000010"));
    private static final ImportJobId IMPORT_ID =
            new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000020"));
    private static final Instant CREATED_AT = Instant.parse("2026-09-11T12:00:00.123456Z");

    @Test
    void computesStableHashAndDefensivelyCopiesSteps() {
        List<ActionPlanStep> mutableSteps = new ArrayList<>(completeSteps());

        ActionPlan first = plan(mutableSteps);
        mutableSteps.clear();
        ActionPlan second = plan(completeSteps());

        assertEquals(first.hash(), second.hash());
        assertEquals(first, second);
        assertNotSame(mutableSteps, first.steps());
        assertEquals(4, first.steps().size());
        assertThrows(UnsupportedOperationException.class, () -> first.steps().clear());
        assertEquals(ActionRisk.HIGH, first.risk());
    }

    @Test
    void hashChangesForEveryApprovalRelevantDimension() {
        ActionPlan baseline = plan(completeSteps());

        assertNotEquals(
                baseline.hash(),
                ActionPlan.propose(
                                PLAN_ID,
                                new ActionPlanVersion(2),
                                IMPORT_ID,
                                CREATED_AT,
                                "agent-subject",
                                completeSteps())
                        .hash());
        assertNotEquals(
                baseline.hash(),
                ActionPlan.propose(
                                PLAN_ID,
                                ActionPlanVersion.initial(),
                                IMPORT_ID,
                                CREATED_AT.plusNanos(1),
                                "agent-subject",
                                completeSteps())
                        .hash());
        assertNotEquals(
                baseline.hash(),
                ActionPlan.propose(
                                PLAN_ID,
                                ActionPlanVersion.initial(),
                                IMPORT_ID,
                                CREATED_AT,
                                "different-subject",
                                completeSteps())
                        .hash());

        List<ActionPlanStep> changedEvidence = new ArrayList<>(completeSteps());
        changedEvidence.set(
                0,
                new CreateSupplierPartyStep(
                        1,
                        partyId(),
                        sourceRecord(),
                        new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(new EvidenceReference(
                                EvidenceType.MATCH_EVALUATION, "match-evaluation-1", 10))));
        assertNotEquals(baseline.hash(), plan(changedEvidence).hash());
    }

    @Test
    void rejectsTamperedReconstitution() {
        ActionPlan original = plan(completeSteps());
        List<ActionPlanStep> changedSteps = new ArrayList<>(completeSteps());
        changedSteps.set(
                1,
                new LinkSourceRecordStep(
                        2,
                        sourceRecord(),
                        partyId(),
                        6,
                        new ActionReasonCode("EXACT_IDENTIFIER_MATCH"),
                        List.of(sourceEvidence())));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ActionPlan(
                        original.id(),
                        original.version(),
                        original.importId(),
                        original.createdAt(),
                        original.proposedBySubject(),
                        changedSteps,
                        original.hash()));
    }

    @Test
    void requiresContiguousOrderedStepsAndAtLeastOneMutation() {
        assertThrows(
                IllegalArgumentException.class,
                () -> plan(List.of(new CreateSupplierPartyStep(
                        2,
                        partyId(),
                        sourceRecord(),
                        new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(sourceEvidence())))));
        assertThrows(IllegalArgumentException.class, () -> plan(List.of()));
    }

    @Test
    void validatesPlanIdentifiersVersionAndHashShape() {
        assertThrows(IllegalArgumentException.class, () -> new ActionPlanVersion(0));
        assertThrows(IllegalArgumentException.class, () -> new ActionPlanHash("ABC"));
        assertEquals(1, ActionPlanVersion.initial().value());
    }

    private static ActionPlan plan(List<ActionPlanStep> steps) {
        return ActionPlan.propose(
                PLAN_ID,
                ActionPlanVersion.initial(),
                IMPORT_ID,
                CREATED_AT,
                "agent-subject",
                steps);
    }

    private static List<ActionPlanStep> completeSteps() {
        BusinessUnitId procurementBu =
                new BusinessUnitId(UUID.fromString("00000000-0000-0000-0000-000000000030"));
        SupplierSiteId siteId =
                new SupplierSiteId(UUID.fromString("00000000-0000-0000-0000-000000000040"));
        return List.of(
                new CreateSupplierPartyStep(
                        1,
                        partyId(),
                        sourceRecord(),
                        new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(
                                sourceEvidence(),
                                new EvidenceReference(
                                        EvidenceType.MATCH_EVALUATION, "match-evaluation-1", 9))),
                new LinkSourceRecordStep(
                        2,
                        sourceRecord(),
                        partyId(),
                        1,
                        new ActionReasonCode("EXACT_IDENTIFIER_MATCH"),
                        List.of(sourceEvidence())),
                new CreateSupplierSiteStep(
                        3,
                        siteId,
                        partyId(),
                        1,
                        new SupplierAddressId(
                                UUID.fromString("00000000-0000-0000-0000-000000000050")),
                        procurementBu,
                        new ActionReasonCode("NEW_OPERATING_SITE"),
                        List.of(sourceEvidence())),
                new AssignSupplierSiteStep(
                        4,
                        new SiteAssignmentId(
                                UUID.fromString("00000000-0000-0000-0000-000000000060")),
                        siteId,
                        1,
                        new BusinessUnitId(
                                UUID.fromString("00000000-0000-0000-0000-000000000070")),
                        Set.of(SitePurpose.PAY, SitePurpose.PURCHASING),
                        LocalDate.parse("2026-09-12"),
                        Optional.empty(),
                        new ActionReasonCode("AUTHORIZE_CLIENT_BU"),
                        List.of(new EvidenceReference(
                                EvidenceType.BUSINESS_UNIT_REFERENCE, "client-bu-1", 3))));
    }

    private static EvidenceReference sourceEvidence() {
        return new EvidenceReference(EvidenceType.SOURCE_RECORD, "erp-a:supplier-42", 7);
    }

    private static SourceRecordIdentity sourceRecord() {
        return new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-42", 7);
    }

    private static SupplierPartyId partyId() {
        return new SupplierPartyId(UUID.fromString("00000000-0000-0000-0000-000000000080"));
    }
}
