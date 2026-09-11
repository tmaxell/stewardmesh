package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.domain.organization.BusinessUnit;

@FunctionalInterface
public interface SynchronizeBusinessUnit extends UseCase<BusinessUnit, BusinessUnit> {}
