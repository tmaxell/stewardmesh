package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;

@FunctionalInterface
public interface GetBusinessUnit extends UseCase<BusinessUnitId, BusinessUnit> {}
