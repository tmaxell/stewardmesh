package io.stewardmesh.masterdata.domain.model;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of an imported internal business-unit reference. */
public record BusinessUnitId(UUID value) {

    public BusinessUnitId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
