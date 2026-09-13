package io.stewardmesh.masterdata.application.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.AssignSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.LinkSourceRecordStep;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttribute;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeProvenance;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordProjector;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierAddress;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierParty;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierSite;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRule;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LocalActionPlanStepApplierTest {

    private static final SupplierPartyId PARTY = id(SupplierPartyId::new, 701);
    private static final SupplierSiteId SITE = id(SupplierSiteId::new, 702);
    private static final SupplierAddressId ADDRESS = id(SupplierAddressId::new, 703);
    private static final BusinessUnitId CLIENT = id(BusinessUnitId::new, 704);
    private static final SiteAssignmentId ASSIGNMENT = id(SiteAssignmentId::new, 705);
    private static final SourceRecordIdentity SOURCE = new SourceRecordIdentity(
            new SourceSystemRef("synthetic-erp"), "supplier-701", 4);
    private static final ActionReasonCode REASON = new ActionReasonCode("SYNTHETIC_TEST");
    private static final EvidenceReference MATCH =
            new EvidenceReference(EvidenceType.MATCH_EVALUATION, "supplier-match-v1", 1);

    @Test
    void projectsSourceBearingStepsWithTheExactEvidenceRuleset() {
        var keys = new ArrayList<IdentityResolutionKey>();
        var projection = new FixedProjection();
        projection.party = Optional.of(party(1));
        var applier = new LocalActionPlanStepApplier(keys::add, projection, assignment -> assignment);

        AppliedActionStep created = applier.apply(new CreateSupplierPartyStep(
                1, PARTY, SOURCE, REASON, List.of(sourceEvidence(), MATCH)));
        projection.party = Optional.of(party(3));
        AppliedActionStep linked = applier.apply(new LinkSourceRecordStep(
                2, SOURCE, PARTY, 2, REASON, List.of(sourceEvidence(), MATCH)));

        assertEquals(List.of("supplier-match-v1", "supplier-match-v1"),
                keys.stream().map(key -> key.rulesetId().value()).toList());
        assertEquals("SupplierCreated", created.eventType());
        assertEquals(1, created.subjectVersion());
        assertEquals("SupplierGoldenRecordChanged", linked.eventType());
        assertEquals(3, linked.subjectVersion());
    }

    @Test
    void rejectsMissingAmbiguousOrInvalidMatchEvidenceBeforeProjection() {
        var projectionCalls = new ArrayList<IdentityResolutionKey>();
        var projection = new FixedProjection();
        projection.party = Optional.of(party(1));
        var applier = new LocalActionPlanStepApplier(
                projectionCalls::add, projection, assignment -> assignment);

        var missing = new CreateSupplierPartyStep(
                1, PARTY, SOURCE, REASON, List.of(sourceEvidence()));
        var ambiguous = new CreateSupplierPartyStep(
                1, PARTY, SOURCE, REASON, List.of(
                        MATCH,
                        new EvidenceReference(
                                EvidenceType.MATCH_EVALUATION, "supplier-match-v2", 2)));
        var invalid = new CreateSupplierPartyStep(
                1, PARTY, SOURCE, REASON,
                List.of(new EvidenceReference(EvidenceType.MATCH_EVALUATION, "latest", 1)));

        assertThrows(ActionPlanStepApplyException.class, () -> applier.apply(missing));
        assertThrows(ActionPlanStepApplyException.class, () -> applier.apply(ambiguous));
        assertThrows(ActionPlanStepApplyException.class, () -> applier.apply(invalid));
        assertEquals(List.of(), projectionCalls);
    }

    @Test
    void verifiesTheMaterializedSiteAgainstTheSealedPlan() {
        var projection = new FixedProjection();
        projection.site = Optional.of(site(2));
        var applier = new LocalActionPlanStepApplier(key -> {}, projection, assignment -> assignment);
        var step = new CreateSupplierSiteStep(
                1, SITE, PARTY, 1, ADDRESS, CLIENT, REASON, List.of(sourceEvidence()));

        AppliedActionStep applied = applier.apply(step);

        assertEquals("SupplierSiteCreated", applied.eventType());
        assertEquals(2, applied.subjectVersion());

        projection.site = Optional.of(siteWithAddress(id(SupplierAddressId::new, 799), 1));
        assertThrows(ActionPlanStepApplyException.class, () -> applier.apply(step));
    }

    @Test
    void delegatesAnExactVersionOneSiteAssignment() {
        AtomicReference<SiteAssignment> candidate = new AtomicReference<>();
        var applier = new LocalActionPlanStepApplier(key -> {}, new FixedProjection(), assignment -> {
            candidate.set(assignment);
            return assignment;
        });
        var step = new AssignSupplierSiteStep(
                1, ASSIGNMENT, SITE, 2, CLIENT, Set.of(SitePurpose.PURCHASING),
                LocalDate.of(2026, 9, 13), Optional.empty(), REASON,
                List.of(new EvidenceReference(EvidenceType.BUSINESS_UNIT_REFERENCE, "client-704", 1)));

        AppliedActionStep applied = applier.apply(step);

        assertEquals(ASSIGNMENT, candidate.get().id());
        assertEquals(1, candidate.get().version());
        assertEquals("SupplierSiteAssigned", applied.eventType());
    }

    @Test
    void refusesAnUnexpectedLinkedPartyVersion() {
        var projection = new FixedProjection();
        projection.party = Optional.of(party(4));
        var applier = new LocalActionPlanStepApplier(key -> {}, projection, assignment -> assignment);
        var step = new LinkSourceRecordStep(
                1, SOURCE, PARTY, 2, REASON, List.of(sourceEvidence(), MATCH));

        assertThrows(ActionPlanStepApplyException.class, () -> applier.apply(step));
    }

    private static EvidenceReference sourceEvidence() {
        return new EvidenceReference(EvidenceType.SOURCE_RECORD, "synthetic-erp:supplier-701", 4);
    }

    private static SupplierParty party(long version) {
        var provenance = provenance();
        return new SupplierParty(
                PARTY, new GoldenRecordVersion(version), GoldenRecordProjector.RULESET_ID,
                provenance.decidedAt(), Map.of(
                        GoldenAttributeName.LEGAL_NAME,
                        new GoldenAttribute(GoldenAttributeName.LEGAL_NAME, "Synthetic Supplier", provenance),
                        GoldenAttributeName.INN,
                        new GoldenAttribute(GoldenAttributeName.INN, "7700000701", provenance)),
                List.of(provenance.associationId()));
    }

    private static SupplierSite site(long version) {
        return siteWithAddress(ADDRESS, version);
    }

    private static SupplierSite siteWithAddress(SupplierAddressId address, long version) {
        var provenance = provenance();
        return new SupplierSite(
                SITE, PARTY, address, new GoldenRecordVersion(version),
                GoldenRecordProjector.RULESET_ID, provenance.decidedAt(), Map.of(
                        GoldenAttributeName.KPP,
                        new GoldenAttribute(GoldenAttributeName.KPP, "770001001", provenance)),
                List.of(provenance.associationId()));
    }

    private static GoldenAttributeProvenance provenance() {
        return new GoldenAttributeProvenance(
                SOURCE, id(SourceAssociationId::new, 706), SurvivorshipRule.TRUSTED_SOURCE,
                GoldenRecordProjector.RULESET_ID, Instant.parse("2026-09-13T08:00:00Z"));
    }

    private static <T> T id(java.util.function.Function<UUID, T> constructor, int suffix) {
        return constructor.apply(UUID.fromString("00000000-0000-0000-0000-000000000" + suffix));
    }

    private static final class FixedProjection implements LoadGoldenRecordProjection {
        private Optional<SupplierParty> party = Optional.empty();
        private Optional<SupplierSite> site = Optional.empty();

        @Override
        public Optional<SupplierParty> findParty(SupplierPartyId partyId) {
            return party;
        }

        @Override
        public Optional<SupplierAddress> findAddress(SupplierAddressId addressId) {
            return Optional.empty();
        }

        @Override
        public Optional<SupplierSite> findSite(SupplierSiteId siteId) {
            return site;
        }
    }
}
