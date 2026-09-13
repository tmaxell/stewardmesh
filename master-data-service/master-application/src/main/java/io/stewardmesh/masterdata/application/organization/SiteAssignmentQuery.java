package io.stewardmesh.masterdata.application.organization;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.Objects;

public record SiteAssignmentQuery(
        SupplierSiteId siteId, BusinessUnitId clientBusinessUnitId, int limit) {

    public static final int MAX_LIMIT = 100;

    public SiteAssignmentQuery {
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(clientBusinessUnitId, "clientBusinessUnitId must not be null");
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("limit must be between 1 and 100");
        }
    }
}
