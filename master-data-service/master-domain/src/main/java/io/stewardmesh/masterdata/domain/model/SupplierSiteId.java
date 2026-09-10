package io.stewardmesh.masterdata.domain.model;

import java.util.Objects;
import java.util.UUID;

public record SupplierSiteId(UUID value) {

    public SupplierSiteId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
