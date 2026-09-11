package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.organization.SiteAssignmentQuery;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import java.util.List;

@FunctionalInterface
public interface ListSiteAssignments extends UseCase<SiteAssignmentQuery, List<SiteAssignment>> {}
