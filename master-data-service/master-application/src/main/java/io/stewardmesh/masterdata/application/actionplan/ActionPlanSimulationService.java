package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.application.organization.SiteAssignmentQuery;
import io.stewardmesh.masterdata.application.port.in.SimulateActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulation;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulator;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep;
import io.stewardmesh.masterdata.domain.actionplan.AssignSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.LinkSourceRecordStep;
import io.stewardmesh.masterdata.domain.actionplan.SimulationSnapshot;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Reads the master data a sealed plan names and hands it to the domain simulator. The use case
 * performs no write, and refuses to answer for a plan version or hash the caller did not bind.
 */
public final class ActionPlanSimulationService implements SimulateActionPlan {

    private final ActionPlanRepository plans;
    private final LoadGoldenRecordProjection goldenRecords;
    private final BusinessUnitRepository businessUnits;
    private final SiteAssignmentRepository assignments;
    private final LoadSourceRecord sourceRecords;
    private final ActionPlanSimulator simulator;

    public ActionPlanSimulationService(
            ActionPlanRepository plans,
            LoadGoldenRecordProjection goldenRecords,
            BusinessUnitRepository businessUnits,
            SiteAssignmentRepository assignments,
            LoadSourceRecord sourceRecords,
            ActionPlanSimulator simulator) {
        this.plans = Objects.requireNonNull(plans, "plans must not be null");
        this.goldenRecords = Objects.requireNonNull(goldenRecords, "goldenRecords must not be null");
        this.businessUnits = Objects.requireNonNull(businessUnits, "businessUnits must not be null");
        this.assignments = Objects.requireNonNull(assignments, "assignments must not be null");
        this.sourceRecords = Objects.requireNonNull(sourceRecords, "sourceRecords must not be null");
        this.simulator = Objects.requireNonNull(simulator, "simulator must not be null");
    }

    @Override
    public ActionPlanSimulation execute(SimulateActionPlanCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        GovernedActionPlan governed =
                plans.findById(command.planId()).orElseThrow(ActionPlanNotFoundException::new);
        requireBoundPlan(governed, command);
        return simulator.simulate(governed.plan(), snapshot(governed.plan()));
    }

    private static void requireBoundPlan(
            GovernedActionPlan governed, SimulateActionPlanCommand command) {
        if (!governed.version().equals(command.expectedVersion())
                || !governed.hash().equals(command.expectedHash())) {
            throw new ActionPlanConflictException(
                    "simulation must bind the exact sealed plan version and hash");
        }
    }

    private SimulationSnapshot snapshot(ActionPlan plan) {
        var partyIds = new LinkedHashSet<SupplierPartyId>();
        var siteIds = new LinkedHashSet<SupplierSiteId>();
        var addressIds = new LinkedHashSet<SupplierAddressId>();
        var businessUnitIds = new LinkedHashSet<BusinessUnitId>();
        var sourceIdentities = new LinkedHashSet<SourceRecordIdentity>();
        var assignmentContexts = new LinkedHashSet<AssignmentContext>();

        plan.steps().forEach(step -> collect(
                step, partyIds, siteIds, addressIds, businessUnitIds, sourceIdentities,
                assignmentContexts));

        return new SimulationSnapshot(
                versions(partyIds, this::partyVersion),
                versions(siteIds, this::siteVersion),
                storedAddresses(addressIds),
                storedBusinessUnits(businessUnitIds),
                storedSourceRecords(sourceIdentities),
                storedAssignments(assignmentContexts));
    }

    private static void collect(
            ActionPlanStep step,
            Set<SupplierPartyId> partyIds,
            Set<SupplierSiteId> siteIds,
            Set<SupplierAddressId> addressIds,
            Set<BusinessUnitId> businessUnitIds,
            Set<SourceRecordIdentity> sourceIdentities,
            Set<AssignmentContext> assignmentContexts) {
        switch (step) {
            case CreateSupplierPartyStep create -> {
                partyIds.add(create.partyId());
                sourceIdentities.add(create.sourceRecord());
            }
            case LinkSourceRecordStep link -> {
                partyIds.add(link.partyId());
                sourceIdentities.add(link.sourceRecord());
            }
            case CreateSupplierSiteStep create -> {
                siteIds.add(create.siteId());
                partyIds.add(create.partyId());
                addressIds.add(create.addressId());
                businessUnitIds.add(create.procurementBusinessUnitId());
            }
            case AssignSupplierSiteStep assign -> {
                siteIds.add(assign.siteId());
                businessUnitIds.add(assign.clientBusinessUnitId());
                assignmentContexts.add(
                        new AssignmentContext(assign.siteId(), assign.clientBusinessUnitId()));
            }
        }
    }

    private static <I> Map<I, Long> versions(
            Set<I> identifiers, java.util.function.Function<I, Long> currentVersion) {
        var versions = new LinkedHashMap<I, Long>();
        identifiers.forEach(identifier -> {
            Long version = currentVersion.apply(identifier);
            if (version != null) {
                versions.put(identifier, version);
            }
        });
        return versions;
    }

    private Long partyVersion(SupplierPartyId partyId) {
        return goldenRecords.findParty(partyId).map(party -> party.version().value()).orElse(null);
    }

    private Long siteVersion(SupplierSiteId siteId) {
        return goldenRecords.findSite(siteId).map(site -> site.version().value()).orElse(null);
    }

    private Set<SupplierAddressId> storedAddresses(Set<SupplierAddressId> addressIds) {
        var stored = new LinkedHashSet<SupplierAddressId>();
        addressIds.stream()
                .filter(addressId -> goldenRecords.findAddress(addressId).isPresent())
                .forEach(stored::add);
        return stored;
    }

    private Map<BusinessUnitId, BusinessUnit> storedBusinessUnits(Set<BusinessUnitId> ids) {
        var stored = new LinkedHashMap<BusinessUnitId, BusinessUnit>();
        ids.forEach(id -> businessUnits.findById(id).ifPresent(unit -> stored.put(id, unit)));
        return stored;
    }

    private Set<SourceRecordIdentity> storedSourceRecords(Set<SourceRecordIdentity> identities) {
        var stored = new LinkedHashSet<SourceRecordIdentity>();
        identities.stream()
                .filter(identity -> sourceRecords.findByIdentity(identity).isPresent())
                .forEach(stored::add);
        return stored;
    }

    private List<SiteAssignment> storedAssignments(Set<AssignmentContext> contexts) {
        var stored = new ArrayList<SiteAssignment>();
        contexts.forEach(context -> stored.addAll(assignments.findForSiteAndClient(
                context.siteId(), context.clientBusinessUnitId(), SiteAssignmentQuery.MAX_LIMIT)));
        return stored;
    }

    /** One site/client pair whose existing authorizations a proposed assignment could collide with. */
    private record AssignmentContext(
            SupplierSiteId siteId, BusinessUnitId clientBusinessUnitId) {}
}
