package io.stewardmesh.masterdata.application.organization;

import io.stewardmesh.masterdata.application.port.in.AssignSupplierSite;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitRole;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentPolicy;
import java.util.Objects;

/** Creates effective-dated client authorizations for existing mastered supplier sites. */
public final class SiteAssignmentService implements AssignSupplierSite {

    private final LoadGoldenRecordProjection goldenRecords;
    private final BusinessUnitRepository businessUnits;
    private final SiteAssignmentRepository assignments;
    private final ApplicationTransaction transaction;
    private final SiteAssignmentPolicy policy;

    public SiteAssignmentService(
            LoadGoldenRecordProjection goldenRecords,
            BusinessUnitRepository businessUnits,
            SiteAssignmentRepository assignments,
            ApplicationTransaction transaction,
            SiteAssignmentPolicy policy) {
        this.goldenRecords = Objects.requireNonNull(goldenRecords, "goldenRecords must not be null");
        this.businessUnits = Objects.requireNonNull(businessUnits, "businessUnits must not be null");
        this.assignments = Objects.requireNonNull(assignments, "assignments must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
    }

    @Override
    public SiteAssignment execute(SiteAssignment candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        return transaction.execute(() -> assign(candidate));
    }

    private SiteAssignment assign(SiteAssignment candidate) {
        var repeated = assignments.findById(candidate.id());
        if (repeated.isPresent()) {
            var existingAssignment = repeated.orElseThrow();
            if (existingAssignment.equals(candidate)) {
                return existingAssignment;
            }
            throw new SiteAssignmentConflictException("site assignment identity has conflicting content");
        }
        if (candidate.version() != 1) {
            throw new SiteAssignmentConflictException("new site assignment must start at version 1");
        }
        if (goldenRecords.findSite(candidate.siteId()).isEmpty()) {
            throw new SiteAssignmentConflictException("supplier site does not exist");
        }
        var client = businessUnits.findById(candidate.clientBusinessUnitId())
                .orElseThrow(BusinessUnitNotFoundException::new);
        if (!client.supportsThroughout(
                BusinessUnitRole.CLIENT, candidate.validFrom(), candidate.validTo())) {
            throw new SiteAssignmentConflictException(
                    "business unit is not a client throughout the assignment interval");
        }
        var existing = assignments.findForSiteAndClient(
                candidate.siteId(), candidate.clientBusinessUnitId(), SiteAssignmentQuery.MAX_LIMIT);
        try {
            policy.validate(candidate, existing);
        } catch (IllegalArgumentException exception) {
            throw new SiteAssignmentConflictException(exception.getMessage(), exception);
        }
        return assignments.save(candidate);
    }
}
