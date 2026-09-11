package io.stewardmesh.masterdata.domain.actionplan;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitRole;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Applies a sealed plan to a snapshot of current master data without mutating anything. Steps are
 * evaluated in sealed order over a projected overlay, so a step may legitimately depend on an
 * entity an earlier step of the same plan creates.
 */
public final class ActionPlanSimulator {

    /** A party or site created by an earlier step of the same plan starts at version one. */
    private static final long INITIAL_VERSION = 1;

    public ActionPlanSimulation simulate(ActionPlan plan, SimulationSnapshot snapshot) {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(snapshot, "snapshot must not be null");

        var projection = new ProjectedState(snapshot);
        List<SimulatedStep> steps = plan.steps().stream()
                .map(step -> new SimulatedStep(step.sequence(), step.type(), projection.apply(step)))
                .toList();
        SimulationOutcome outcome = steps.stream().allMatch(SimulatedStep::isExecutable)
                ? SimulationOutcome.EXECUTABLE
                : SimulationOutcome.BLOCKED;
        return new ActionPlanSimulation(
                plan.id(), plan.version(), plan.hash(), outcome, steps);
    }

    /** Current facts plus everything the plan would have created by the current step. */
    private static final class ProjectedState {

        private final SimulationSnapshot snapshot;
        private final Map<SupplierPartyId, Long> parties = new HashMap<>();
        private final Map<SupplierSiteId, Long> sites = new HashMap<>();
        private final Set<SiteAssignmentId> assignmentIds = new HashSet<>();
        private final List<SiteAssignment> assignments = new ArrayList<>();

        private ProjectedState(SimulationSnapshot snapshot) {
            this.snapshot = snapshot;
            parties.putAll(snapshot.partyVersions());
            sites.putAll(snapshot.siteVersions());
            snapshot.assignments().forEach(assignment -> {
                assignmentIds.add(assignment.id());
                assignments.add(assignment);
            });
        }

        private List<PreconditionCode> apply(ActionPlanStep step) {
            return switch (step) {
                case CreateSupplierPartyStep create -> createParty(create);
                case LinkSourceRecordStep link -> linkSourceRecord(link);
                case CreateSupplierSiteStep create -> createSite(create);
                case AssignSupplierSiteStep assign -> assignSite(assign);
            };
        }

        private List<PreconditionCode> createParty(CreateSupplierPartyStep step) {
            var violations = new ArrayList<PreconditionCode>();
            if (parties.containsKey(step.partyId())) {
                violations.add(
                        snapshot.partyVersions().containsKey(step.partyId())
                                ? PreconditionCode.PARTY_ALREADY_EXISTS
                                : PreconditionCode.STEP_TARGET_DUPLICATED);
            }
            requireSourceRecord(step.sourceRecord(), violations);
            if (violations.isEmpty()) {
                parties.put(step.partyId(), INITIAL_VERSION);
            }
            return violations;
        }

        private List<PreconditionCode> linkSourceRecord(LinkSourceRecordStep step) {
            var violations = new ArrayList<PreconditionCode>();
            requireSourceRecord(step.sourceRecord(), violations);
            requireParty(step.partyId(), step.expectedPartyVersion(), violations);
            return violations;
        }

        private List<PreconditionCode> createSite(CreateSupplierSiteStep step) {
            var violations = new ArrayList<PreconditionCode>();
            if (sites.containsKey(step.siteId())) {
                violations.add(
                        snapshot.siteVersions().containsKey(step.siteId())
                                ? PreconditionCode.SITE_ALREADY_EXISTS
                                : PreconditionCode.STEP_TARGET_DUPLICATED);
            }
            requireParty(step.partyId(), step.expectedPartyVersion(), violations);
            if (!snapshot.addressIds().contains(step.addressId())) {
                violations.add(PreconditionCode.ADDRESS_NOT_FOUND);
            }
            requireRole(step.procurementBusinessUnitId(), BusinessUnitRole.PROCUREMENT, violations);
            if (violations.isEmpty()) {
                sites.put(step.siteId(), INITIAL_VERSION);
            }
            return violations;
        }

        private List<PreconditionCode> assignSite(AssignSupplierSiteStep step) {
            var violations = new ArrayList<PreconditionCode>();
            if (assignmentIds.contains(step.assignmentId())) {
                violations.add(PreconditionCode.ASSIGNMENT_ALREADY_EXISTS);
            }
            requireSite(step.siteId(), step.expectedSiteVersion(), violations);
            requireClient(step, violations);

            var candidate = new SiteAssignment(
                    step.assignmentId(),
                    step.siteId(),
                    step.clientBusinessUnitId(),
                    step.purposes(),
                    step.validFrom(),
                    step.validTo(),
                    INITIAL_VERSION);
            if (assignments.stream().anyMatch(candidate::conflictsWith)) {
                violations.add(PreconditionCode.ASSIGNMENT_OVERLAPS_EXISTING);
            }
            if (violations.isEmpty()) {
                assignmentIds.add(candidate.id());
                assignments.add(candidate);
            }
            return violations;
        }

        private void requireSourceRecord(
                SourceRecordIdentity source, List<PreconditionCode> violations) {
            if (!snapshot.sourceRecords().contains(source)) {
                violations.add(PreconditionCode.SOURCE_RECORD_NOT_FOUND);
            }
        }

        private void requireParty(
                SupplierPartyId partyId, long expectedVersion, List<PreconditionCode> violations) {
            Long current = parties.get(partyId);
            if (current == null) {
                violations.add(PreconditionCode.PARTY_NOT_FOUND);
            } else if (current != expectedVersion) {
                violations.add(PreconditionCode.PARTY_VERSION_STALE);
            }
        }

        private void requireSite(
                SupplierSiteId siteId, long expectedVersion, List<PreconditionCode> violations) {
            Long current = sites.get(siteId);
            if (current == null) {
                violations.add(PreconditionCode.SITE_NOT_FOUND);
            } else if (current != expectedVersion) {
                violations.add(PreconditionCode.SITE_VERSION_STALE);
            }
        }

        private void requireClient(
                AssignSupplierSiteStep step, List<PreconditionCode> violations) {
            var client = snapshot.businessUnits().get(step.clientBusinessUnitId());
            if (client == null) {
                violations.add(PreconditionCode.BUSINESS_UNIT_NOT_FOUND);
                return;
            }
            if (!client.supportsThroughout(
                    BusinessUnitRole.CLIENT, step.validFrom(), step.validTo())) {
                violations.add(PreconditionCode.BUSINESS_UNIT_ROLE_INVALID);
            }
        }

        private void requireRole(
                BusinessUnitId id, BusinessUnitRole role, List<PreconditionCode> violations) {
            var unit = snapshot.businessUnits().get(id);
            if (unit == null) {
                violations.add(PreconditionCode.BUSINESS_UNIT_NOT_FOUND);
            } else if (!unit.roles().contains(role)) {
                violations.add(PreconditionCode.BUSINESS_UNIT_ROLE_INVALID);
            }
        }
    }
}
