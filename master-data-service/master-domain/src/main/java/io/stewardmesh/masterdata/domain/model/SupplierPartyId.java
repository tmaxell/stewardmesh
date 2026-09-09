package io.stewardmesh.masterdata.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a mastered supplier party. */
public record SupplierPartyId(UUID value) {

    public SupplierPartyId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
