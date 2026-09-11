package io.stewardmesh.masterdata.domain.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionPlanSimulatorTest {

    private static final SupplierPartyId PARTY =
            new SupplierPartyId(UUID.fromString("00000000-0000-0000-0000-000000000501"));
    private static final SupplierSiteId SITE =
            new SupplierSiteId(UUID.fromString("00000000-0000-0000-0000-000000000502"));
    private static final SupplierAddressId ADDRESS =
            new SupplierAddressId(UUID.fromString("00000000-0000-0000-0000-000000000503"));
    private static final BusinessUnitId PROCUREMENT_BU =
            new BusinessUnitId(UUID.fromString("00000000-0000-0000-0000-000000000504"));
    private static final BusinessUnitId CLIENT_BU =
            new BusinessUnitId(UUID.fromString("00000000-0000-0000-0000-000000000505"));
    private static final SiteAssignmentId ASSIGNMENT =
            new SiteAssignmentId(UUID.fromString("00000000-0000-0000-0000-000000000506"));
    private static final LocalDate FROM = LocalDate.of(2026, 9, 12);

    private final ActionPlanSimulator simulator = new ActionPlanSimulator();

    @Test
    void acceptsAPlanWhoseLaterStepsDependOnItsOwnEarlierSteps() {
        ActionPlan plan = plan(List.of(
                createParty(1),
                new CreateSupplierSiteStep(
                        2, SITE, PARTY, 1, ADDRESS, PROCUREMENT_BU, reason(), evidence()),
                new AssignSupplierSiteStep(
                        3,
                        ASSIGNMENT,
                        SITE,
                        1,
                        CLIENT_BU,
                        Set.of(SitePurpose.PURCHASING),
                        FROM,
                        Optional.empty(),
                        reason(),
                        evidence())));

        ActionPlanSimulation simulation = simulator.simulate(plan, snapshot());

        assertEquals(SimulationOutcome.EXECUTABLE, simulation.outcome());
        assertEquals(List.of(), simulation.violations());
        assertTrue(simulation.steps().stream().allMatch(SimulatedStep::isExecutable));
        assertEquals(plan.id(), simulation.planId());
        assertEquals(plan.version(), simulation.planVersion());
        assertEquals(plan.hash(), simulation.planHash());
    }

    @Test
    void reportsMissingSourceRecordsAndExistingTargets() {
        ActionPlan plan = plan(List.of(createParty(1, unknownSource())));
        SimulationSnapshot existing = new SimulationSnapshot(
                Map.of(PARTY, 3L), Map.of(), Set.of(), Map.of(), Set.of(), List.of());

        ActionPlanSimulation simulation = simulator.simulate(plan, existing);

        assertEquals(SimulationOutcome.BLOCKED, simulation.outcome());
        assertEquals(
                List.of(
                        PreconditionCode.SOURCE_RECORD_NOT_FOUND,
                        PreconditionCode.PARTY_ALREADY_EXISTS),
                simulation.violations());
    }

    @Test
    void reportsAStaleExpectedVersionAndAnAbsentParty() {
        ActionPlan stale = plan(List.of(new LinkSourceRecordStep(
                1, source(), PARTY, 9, reason(), evidence())));
        SimulationSnapshot current = new SimulationSnapshot(
                Map.of(PARTY, 3L), Map.of(), Set.of(), Map.of(), Set.of(source()), List.of());

        assertEquals(
                List.of(PreconditionCode.PARTY_VERSION_STALE),
                simulator.simulate(stale, current).violations());
        assertEquals(
                List.of(PreconditionCode.PARTY_NOT_FOUND),
                simulator.simulate(stale, withSource()).violations());
    }

    @Test
    void reportsUnknownAddressesAndBusinessUnitsThatCannotPlayTheRole() {
        ActionPlan plan = plan(List.of(new CreateSupplierSiteStep(
                1, SITE, PARTY, 3, ADDRESS, CLIENT_BU, reason(), evidence())));
        SimulationSnapshot withoutAddress = new SimulationSnapshot(
                Map.of(PARTY, 3L),
                Map.of(),
                Set.of(),
                Map.of(CLIENT_BU, businessUnit(CLIENT_BU, BusinessUnitRole.CLIENT)),
                Set.of(),
                List.of());

        ActionPlanSimulation simulation = simulator.simulate(plan, withoutAddress);

        assertEquals(
                List.of(
                        PreconditionCode.ADDRESS_NOT_FOUND,
                        PreconditionCode.BUSINESS_UNIT_ROLE_INVALID),
                simulation.violations());
        assertEquals(
                List.of(PreconditionCode.SITE_ALREADY_EXISTS, PreconditionCode.ADDRESS_NOT_FOUND),
                simulator
                        .simulate(
                                plan,
                                new SimulationSnapshot(
                                        Map.of(PARTY, 3L),
                                        Map.of(SITE, 1L),
                                        Set.of(),
                                        Map.of(
                                                CLIENT_BU,
                                                businessUnit(
                                                        CLIENT_BU,
                                                        BusinessUnitRole.PROCUREMENT)),
                                        Set.of(),
                                        List.of()))
                        .violations());
    }

    @Test
    void reportsAssignmentsThatCollideWithStoredOrPlannedAuthorizations() {
        SiteAssignment stored = new SiteAssignment(
                new SiteAssignmentId(UUID.fromString("00000000-0000-0000-0000-000000000507")),
                SITE,
                CLIENT_BU,
                Set.of(SitePurpose.PURCHASING),
                FROM.minusMonths(1),
                Optional.empty(),
                1);
        ActionPlan plan = plan(List.of(assign(1, ASSIGNMENT)));
        SimulationSnapshot snapshot = new SimulationSnapshot(
                Map.of(),
                Map.of(SITE, 1L),
                Set.of(),
                Map.of(CLIENT_BU, businessUnit(CLIENT_BU, BusinessUnitRole.CLIENT)),
                Set.of(),
                List.of(stored));

        assertEquals(
                List.of(PreconditionCode.ASSIGNMENT_OVERLAPS_EXISTING),
                simulator.simulate(plan, snapshot).violations());
        assertEquals(
                List.of(PreconditionCode.ASSIGNMENT_ALREADY_EXISTS),
                simulator
                        .simulate(
                                plan(List.of(assign(1, stored.id()))),
                                new SimulationSnapshot(
                                        Map.of(),
                                        Map.of(SITE, 1L),
                                        Set.of(),
                                        Map.of(
                                                CLIENT_BU,
                                                businessUnit(CLIENT_BU, BusinessUnitRole.CLIENT)),
                                        Set.of(),
                                        List.of(reassigned(stored))))
                        .violations());
    }

    @Test
    void reportsUnknownSitesStaleSiteVersionsAndDuplicatePlanTargets() {
        assertEquals(
                List.of(PreconditionCode.SITE_NOT_FOUND),
                simulator.simulate(plan(List.of(assign(1, ASSIGNMENT))), clientOnly()).violations());
        assertEquals(
                List.of(PreconditionCode.SITE_VERSION_STALE),
                simulator
                        .simulate(
                                plan(List.of(assign(1, ASSIGNMENT))),
                                new SimulationSnapshot(
                                        Map.of(),
                                        Map.of(SITE, 4L),
                                        Set.of(),
                                        Map.of(
                                                CLIENT_BU,
                                                businessUnit(CLIENT_BU, BusinessUnitRole.CLIENT)),
                                        Set.of(),
                                        List.of()))
                        .violations());
        assertEquals(
                List.of(PreconditionCode.STEP_TARGET_DUPLICATED),
                simulator
                        .simulate(plan(List.of(createParty(1), createParty(2))), withSource())
                        .violations());
    }

    @Test
    void reportsAnAbsentBusinessUnitAndRejectsInconsistentResults() {
        assertEquals(
                List.of(
                        PreconditionCode.SITE_NOT_FOUND,
                        PreconditionCode.BUSINESS_UNIT_NOT_FOUND),
                simulator
                        .simulate(plan(List.of(assign(1, ASSIGNMENT))), SimulationSnapshot.empty())
                        .violations());

        ActionPlan plan = plan(List.of(createParty(1)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ActionPlanSimulation(
                        plan.id(),
                        plan.version(),
                        plan.hash(),
                        SimulationOutcome.EXECUTABLE,
                        List.of(new SimulatedStep(
                                1,
                                ActionType.CREATE_SUPPLIER_PARTY,
                                List.of(PreconditionCode.PARTY_NOT_FOUND)))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ActionPlanSimulation(
                        plan.id(),
                        plan.version(),
                        plan.hash(),
                        SimulationOutcome.EXECUTABLE,
                        List.of()));
        assertThrows(NullPointerException.class, () -> simulator.simulate(plan, null));
    }

    private static SimulationSnapshot snapshot() {
        return new SimulationSnapshot(
                Map.of(),
                Map.of(),
                Set.of(ADDRESS),
                Map.of(
                        PROCUREMENT_BU,
                        businessUnit(PROCUREMENT_BU, BusinessUnitRole.PROCUREMENT),
                        CLIENT_BU,
                        businessUnit(CLIENT_BU, BusinessUnitRole.CLIENT)),
                Set.of(source()),
                List.of());
    }

    private static SimulationSnapshot withSource() {
        return new SimulationSnapshot(
                Map.of(), Map.of(), Set.of(), Map.of(), Set.of(source()), List.of());
    }

    private static SimulationSnapshot clientOnly() {
        return new SimulationSnapshot(
                Map.of(),
                Map.of(),
                Set.of(),
                Map.of(CLIENT_BU, businessUnit(CLIENT_BU, BusinessUnitRole.CLIENT)),
                Set.of(),
                List.of());
    }

    private static SiteAssignment reassigned(SiteAssignment stored) {
        return new SiteAssignment(
                stored.id(),
                stored.siteId(),
                stored.clientBusinessUnitId(),
                Set.of(SitePurpose.SOURCING),
                FROM.minusYears(3),
                Optional.of(FROM.minusYears(2)),
                stored.version());
    }

    private static AssignSupplierSiteStep assign(int sequence, SiteAssignmentId assignmentId) {
        return new AssignSupplierSiteStep(
                sequence,
                assignmentId,
                SITE,
                1,
                CLIENT_BU,
                Set.of(SitePurpose.PURCHASING),
                FROM,
                Optional.empty(),
                reason(),
                evidence());
    }

    private static BusinessUnit businessUnit(BusinessUnitId id, BusinessUnitRole role) {
        return new BusinessUnit(
                id,
                new BusinessUnitCode("BU-" + role.name()),
                "Synthetic " + role.name(),
                Set.of(role),
                1,
                FROM.minusYears(1),
                Optional.empty());
    }

    private static CreateSupplierPartyStep createParty(int sequence) {
        return createParty(sequence, source());
    }

    private static CreateSupplierPartyStep createParty(
            int sequence, SourceRecordIdentity sourceRecord) {
        return new CreateSupplierPartyStep(sequence, PARTY, sourceRecord, reason(), evidence());
    }

    private static ActionPlan plan(List<ActionPlanStep> steps) {
        return ActionPlan.propose(
                new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000508")),
                ActionPlanVersion.initial(),
                new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000509")),
                Instant.parse("2026-09-12T11:00:00Z"),
                "steward-1",
                steps);
    }

    private static ActionReasonCode reason() {
        return new ActionReasonCode("SIMULATED_STEP");
    }

    private static List<EvidenceReference> evidence() {
        return List.of(new EvidenceReference(EvidenceType.SOURCE_RECORD, "erp-a:supplier-51", 2));
    }

    private static SourceRecordIdentity source() {
        return new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-51", 2);
    }

    private static SourceRecordIdentity unknownSource() {
        return new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-missing", 1);
    }
}
