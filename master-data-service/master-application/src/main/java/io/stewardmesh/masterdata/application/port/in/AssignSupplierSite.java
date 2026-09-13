package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.domain.organization.SiteAssignment;

@FunctionalInterface
public interface AssignSupplierSite extends UseCase<SiteAssignment, SiteAssignment> {}
