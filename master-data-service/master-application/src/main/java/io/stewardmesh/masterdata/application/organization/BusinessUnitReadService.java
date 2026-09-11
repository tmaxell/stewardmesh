package io.stewardmesh.masterdata.application.organization;

import io.stewardmesh.masterdata.application.port.in.GetBusinessUnit;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import java.util.Objects;

public final class BusinessUnitReadService implements GetBusinessUnit {

    private final BusinessUnitRepository businessUnits;

    public BusinessUnitReadService(BusinessUnitRepository businessUnits) {
        this.businessUnits = Objects.requireNonNull(businessUnits, "businessUnits must not be null");
    }

    @Override
    public BusinessUnit execute(BusinessUnitId id) {
        Objects.requireNonNull(id, "id must not be null");
        return businessUnits.findById(id).orElseThrow(BusinessUnitNotFoundException::new);
    }
}
