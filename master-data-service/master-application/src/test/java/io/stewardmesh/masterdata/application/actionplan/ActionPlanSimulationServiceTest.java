package io.stewardmesh.masterdata.application.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulation;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulator;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.AssignSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.PreconditionCode;
import io.stewardmesh.masterdata.domain.actionplan.SimulationOutcome;
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
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitCode;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitRole;
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
import org.junit.jupiter.api.Test;

class ActionPlanSimulationServiceTest {

    private static final ActionPlanId PLAN_ID =
            new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000601"));
    private static final SupplierSiteId SITE =
            new SupplierSiteId(UUID.fromString("00000000-0000-0000-0000-000000000602"));
    private static final BusinessUnitId CLIENT_BU =
            new BusinessUnitId(UUID.fromString("00000000-0000-0000-0000-000000000603"));
    private static final SupplierPartyId PARTY =
            new SupplierPartyId(UUID.fromString("00000000-0000-0000-0000-000000000604"));
    private static final LocalDate FROM = LocalDate.of(2026, 9, 12);

    private final InMemoryActionPlanRepository plans = new InMemoryActionPlanRepository();
    private final List<String> reads = new ArrayList<>();

    @Test
    void simulatesTheBoundPlanAgainstOnlyTheMasterDataItNames() {
        GovernedActionPlan governed = store(List.of(createParty(1), assign(2)));

        ActionPlanSimulation simulation = service().execute(new SimulateActionPlanCommand(
                PLAN_ID, governed.version(), governed.hash()));

        assertEquals(SimulationOutcome.BLOCKED, simulation.outcome());
        assertEquals(
                List.of(
                        PreconditionCode.SOURCE_RECORD_NOT_FOUND,
                        PreconditionCode.SITE_NOT_FOUND,
                        PreconditionCode.BUSINESS_UNIT_NOT_FOUND),
                simulation.violations());
        assertEquals(governed.hash(), simulation.planHash());
        assertEquals(
                List.of(
                        "party:" + PARTY.value(),
                        "site:" + SITE.value(),
                        "source:supplier-61",
                        "assignments:" + SITE.value()),
                reads);
    }

    @Test
    void reportsAnExecutablePlanWhenEveryReferencedFactIsPresent() {
        GovernedActionPlan governed = store(List.of(assign(1)));

        ActionPlanSimulation simulation = service(
                        site(1), businessUnit(BusinessUnitRole.CLIENT), List.of())
                .execute(new SimulateActionPlanCommand(
                        PLAN_ID, governed.version(), governed.hash()));

        assertEquals(SimulationOutcome.EXECUTABLE, simulation.outcome());
        assertEquals(List.of(), simulation.violations());
    }

    @Test
    void seesAuthorizationsThatAlreadyCoverTheSameSiteClientAndPurpose() {
        GovernedActionPlan governed = store(List.of(assign(1)));
        SiteAssignment stored = new SiteAssignment(
                new SiteAssignmentId(UUID.fromString("00000000-0000-0000-0000-000000000605")),
                SITE,
                CLIENT_BU,
                Set.of(SitePurpose.PURCHASING),
                FROM.minusMonths(2),
                Optional.empty(),
                1);

        ActionPlanSimulation simulation = service(
                        site(1), businessUnit(BusinessUnitRole.CLIENT), List.of(stored))
                .execute(new SimulateActionPlanCommand(
                        PLAN_ID, governed.version(), governed.hash()));

        assertEquals(
                List.of(PreconditionCode.ASSIGNMENT_OVERLAPS_EXISTING), simulation.violations());
    }

    @Test
    void refusesToAnswerForAPlanVersionOrHashTheCallerDidNotBind() {
        GovernedActionPlan governed = store(List.of(assign(1)));
        var service = service();

        var wrongVersion = new SimulateActionPlanCommand(
                PLAN_ID, new ActionPlanVersion(2), governed.hash());
        var wrongHash = new SimulateActionPlanCommand(
                PLAN_ID, governed.version(), new ActionPlanHash("b".repeat(64)));
        var unknown = new SimulateActionPlanCommand(
                new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000606")),
                governed.version(),
                governed.hash());

        assertThrows(ActionPlanConflictException.class, () -> service.execute(wrongVersion));
        assertThrows(ActionPlanConflictException.class, () -> service.execute(wrongHash));
        assertThrows(ActionPlanNotFoundException.class, () -> service.execute(unknown));
        assertThrows(NullPointerException.class, () -> service.execute(null));
        assertEquals(List.of(), reads);
    }

    private GovernedActionPlan store(List<ActionPlanStep> steps) {
        GovernedActionPlan governed = GovernedActionPlan.proposed(ActionPlan.propose(
                PLAN_ID,
                ActionPlanVersion.initial(),
                new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000607")),
                Instant.parse("2026-09-12T12:00:00Z"),
                "steward-1",
                steps));
        plans.replace(governed);
        return governed;
    }

    private ActionPlanSimulationService service() {
        return service(Optional.empty(), Optional.empty(), List.of());
    }

    private ActionPlanSimulationService service(
            Optional<SupplierSite> storedSite,
            Optional<BusinessUnit> storedUnit,
            List<SiteAssignment> storedAssignments) {
        LoadGoldenRecordProjection goldenRecords = new LoadGoldenRecordProjection() {
            @Override
            public Optional<SupplierParty> findParty(SupplierPartyId partyId) {
                reads.add("party:" + partyId.value());
                return Optional.empty();
            }

            @Override
            public Optional<SupplierAddress> findAddress(SupplierAddressId addressId) {
                reads.add("address:" + addressId.value());
                return Optional.empty();
            }

            @Override
            public Optional<SupplierSite> findSite(SupplierSiteId siteId) {
                reads.add("site:" + siteId.value());
                return storedSite;
            }
        };
        BusinessUnitRepository units = new BusinessUnitRepository() {
            @Override
            public Optional<BusinessUnit> findById(BusinessUnitId id) {
                return storedUnit;
            }

            @Override
            public Optional<BusinessUnit> findByCode(BusinessUnitCode code) {
                return Optional.empty();
            }

            @Override
            public BusinessUnit save(BusinessUnit businessUnit) {
                throw new AssertionError("simulation must not write");
            }
        };
        SiteAssignmentRepository assignments = new SiteAssignmentRepository() {
            @Override
            public Optional<SiteAssignment> findById(SiteAssignmentId id) {
                throw new AssertionError("simulation reads authorizations by context");
            }

            @Override
            public List<SiteAssignment> findForSiteAndClient(
                    SupplierSiteId siteId, BusinessUnitId clientBusinessUnitId, int limit) {
                reads.add("assignments:" + siteId.value());
                return storedAssignments;
            }

            @Override
            public SiteAssignment save(SiteAssignment assignment) {
                throw new AssertionError("simulation must not write");
            }
        };
        LoadSourceRecord sources = identity -> {
            reads.add("source:" + identity.sourceRecordId());
            return Optional.<SourceRecord>empty();
        };
        return new ActionPlanSimulationService(
                plans, goldenRecords, units, assignments, sources, new ActionPlanSimulator());
    }

    private static Optional<SupplierSite> site(long version) {
        var decidedAt = Instant.parse("2026-09-11T08:00:00Z");
        var association =
                new SourceAssociationId(UUID.fromString("00000000-0000-0000-0000-000000000609"));
        var provenance = new GoldenAttributeProvenance(
                new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-61", 3),
                association,
                SurvivorshipRule.TRUSTED_SOURCE,
                GoldenRecordProjector.RULESET_ID,
                decidedAt);
        return Optional.of(new SupplierSite(
                SITE,
                PARTY,
                new SupplierAddressId(UUID.fromString("00000000-0000-0000-0000-000000000610")),
                new GoldenRecordVersion(version),
                GoldenRecordProjector.RULESET_ID,
                decidedAt,
                Map.of(
                        GoldenAttributeName.KPP,
                        new GoldenAttribute(GoldenAttributeName.KPP, "990201001", provenance)),
                List.of(association)));
    }

    private static Optional<BusinessUnit> businessUnit(BusinessUnitRole role) {
        return Optional.of(new BusinessUnit(
                CLIENT_BU,
                new BusinessUnitCode("BU-CLIENT"),
                "Synthetic Client",
                Set.of(role),
                1,
                FROM.minusYears(1),
                Optional.empty()));
    }

    private static CreateSupplierPartyStep createParty(int sequence) {
        return new CreateSupplierPartyStep(
                sequence,
                PARTY,
                new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-61", 3),
                new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                List.of(new EvidenceReference(EvidenceType.SOURCE_RECORD, "erp-a:supplier-61", 3)));
    }

    private static AssignSupplierSiteStep assign(int sequence) {
        return new AssignSupplierSiteStep(
                sequence,
                new SiteAssignmentId(UUID.fromString("00000000-0000-0000-0000-000000000608")),
                SITE,
                1,
                CLIENT_BU,
                Set.of(SitePurpose.PURCHASING),
                FROM,
                Optional.empty(),
                new ActionReasonCode("AUTHORIZE_CLIENT_BU"),
                List.of(new EvidenceReference(
                        EvidenceType.BUSINESS_UNIT_REFERENCE, "client-bu-1", 1)));
    }
}
