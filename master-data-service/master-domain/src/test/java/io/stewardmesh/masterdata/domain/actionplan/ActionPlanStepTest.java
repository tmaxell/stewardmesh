package io.stewardmesh.masterdata.domain.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionPlanStepTest {

    private static final ActionReasonCode NEW_PARTY = new ActionReasonCode("NO_MATCH_NEW_PARTY");

    @Test
    void requiresVersionedEvidenceAndDefensivelyCopiesIt() {
        List<EvidenceReference> input = new ArrayList<>(List.of(
                evidence(EvidenceType.MATCH_EVALUATION, "evaluation-1", 3),
                evidence(EvidenceType.SOURCE_RECORD, "erp-a:supplier-42", 7)));

        CreateSupplierPartyStep step = new CreateSupplierPartyStep(
                1, partyId(), sourceRecord(), NEW_PARTY, input);
        input.clear();

        assertEquals(2, step.evidence().size());
        assertNotSame(input, step.evidence());
        assertThrows(UnsupportedOperationException.class, () -> step.evidence().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CreateSupplierPartyStep(
                        1, partyId(), sourceRecord(), NEW_PARTY, List.of()));
    }

    @Test
    void normalizesEvidenceOrderAndRejectsDuplicates() {
        EvidenceReference source = evidence(EvidenceType.SOURCE_RECORD, "erp-a:supplier-42", 7);
        EvidenceReference evaluation = evidence(EvidenceType.MATCH_EVALUATION, "evaluation-1", 3);

        CreateSupplierPartyStep step = new CreateSupplierPartyStep(
                1, partyId(), sourceRecord(), NEW_PARTY, List.of(source, evaluation));

        assertEquals(List.of(evaluation, source), step.evidence());
        assertThrows(
                IllegalArgumentException.class,
                () -> new CreateSupplierPartyStep(
                        1, partyId(), sourceRecord(), NEW_PARTY, List.of(source, source)));
    }

    @Test
    void derivesRiskFromClosedActionType() {
        CreateSupplierPartyStep create = new CreateSupplierPartyStep(
                1, partyId(), sourceRecord(), NEW_PARTY, List.of(sourceEvidence()));
        LinkSourceRecordStep link = new LinkSourceRecordStep(
                2,
                sourceRecord(),
                partyId(),
                4,
                new ActionReasonCode("EXACT_IDENTIFIER_MATCH"),
                List.of(sourceEvidence()));

        assertEquals(ActionRisk.MEDIUM, create.risk());
        assertEquals(ActionRisk.HIGH, link.risk());
    }

    @Test
    void requiresExpectedAggregateVersions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new LinkSourceRecordStep(
                        1,
                        sourceRecord(),
                        partyId(),
                        0,
                        new ActionReasonCode("EXACT_IDENTIFIER_MATCH"),
                        List.of(sourceEvidence())));

        assertThrows(
                IllegalArgumentException.class,
                () -> new CreateSupplierSiteStep(
                        1,
                        siteId(),
                        partyId(),
                        -1,
                        new SupplierAddressId(UUID.randomUUID()),
                        new BusinessUnitId(UUID.randomUUID()),
                        new ActionReasonCode("NEW_OPERATING_SITE"),
                        List.of(sourceEvidence())));
    }

    @Test
    void assignmentStepCopiesPurposesAndValidatesEffectiveDates() {
        AssignSupplierSiteStep step = new AssignSupplierSiteStep(
                1,
                new SiteAssignmentId(UUID.randomUUID()),
                siteId(),
                2,
                new BusinessUnitId(UUID.randomUUID()),
                Set.of(SitePurpose.PURCHASING, SitePurpose.PAY),
                LocalDate.parse("2026-09-01"),
                Optional.empty(),
                new ActionReasonCode("AUTHORIZE_CLIENT_BU"),
                List.of(evidence(EvidenceType.BUSINESS_UNIT_REFERENCE, "client-bu-1", 5)));

        assertEquals(Set.of(SitePurpose.PURCHASING, SitePurpose.PAY), step.purposes());
        assertThrows(UnsupportedOperationException.class, () -> step.purposes().clear());
        assertThrows(
                IllegalArgumentException.class,
                () -> new AssignSupplierSiteStep(
                        1,
                        new SiteAssignmentId(UUID.randomUUID()),
                        siteId(),
                        2,
                        new BusinessUnitId(UUID.randomUUID()),
                        Set.of(SitePurpose.PURCHASING),
                        LocalDate.parse("2026-09-02"),
                        Optional.of(LocalDate.parse("2026-09-01")),
                        new ActionReasonCode("AUTHORIZE_CLIENT_BU"),
                        List.of(sourceEvidence())));
    }

    @Test
    void rejectsFreeFormReasonCodesAndUnversionedEvidence() {
        assertThrows(IllegalArgumentException.class, () -> new ActionReasonCode("please merge supplier"));
        assertThrows(
                IllegalArgumentException.class,
                () -> evidence(EvidenceType.SOURCE_RECORD, "source", 0));
    }

    private static EvidenceReference sourceEvidence() {
        return evidence(EvidenceType.SOURCE_RECORD, "erp-a:supplier-42", 7);
    }

    private static EvidenceReference evidence(EvidenceType type, String reference, long version) {
        return new EvidenceReference(type, reference, version);
    }

    private static SourceRecordIdentity sourceRecord() {
        return new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-42", 7);
    }

    private static SupplierPartyId partyId() {
        return new SupplierPartyId(UUID.fromString("10000000-0000-0000-0000-000000000001"));
    }

    private static SupplierSiteId siteId() {
        return new SupplierSiteId(UUID.fromString("20000000-0000-0000-0000-000000000002"));
    }
}
