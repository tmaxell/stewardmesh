package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitCode;
import java.util.Optional;

public interface BusinessUnitRepository {

    Optional<BusinessUnit> findById(BusinessUnitId id);

    Optional<BusinessUnit> findByCode(BusinessUnitCode code);

    BusinessUnit save(BusinessUnit businessUnit);
}
