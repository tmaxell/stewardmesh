package io.stewardmesh.masterdata.application.organization;

import io.stewardmesh.masterdata.application.port.in.ListSiteAssignments;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import java.util.List;
import java.util.Objects;

public final class SiteAssignmentReadService implements ListSiteAssignments {

    private final SiteAssignmentRepository assignments;

    public SiteAssignmentReadService(SiteAssignmentRepository assignments) {
        this.assignments = Objects.requireNonNull(assignments, "assignments must not be null");
    }

    @Override
    public List<SiteAssignment> execute(SiteAssignmentQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return List.copyOf(assignments.findForSiteAndClient(
                query.siteId(), query.clientBusinessUnitId(), query.limit()));
    }
}
