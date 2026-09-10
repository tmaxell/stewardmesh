package io.stewardmesh.masterdata.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of an address asserted for a mastered supplier party. */
public record SupplierAddressId(UUID value) {

    public SupplierAddressId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
