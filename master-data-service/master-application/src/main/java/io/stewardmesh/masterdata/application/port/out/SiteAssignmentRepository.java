package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import java.util.List;
import java.util.Optional;

public interface SiteAssignmentRepository {

    Optional<SiteAssignment> findById(SiteAssignmentId id);

    List<SiteAssignment> findForSiteAndClient(
            SupplierSiteId siteId, BusinessUnitId clientBusinessUnitId, int limit);

    /** Returns only assignments whose purpose and effective interval overlap the candidate. */
    List<SiteAssignment> findConflicts(SiteAssignment candidate, int limit);

    SiteAssignment save(SiteAssignment assignment);
}
