package io.stewardmesh.masterdata.domain.intake;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a supplier import. */
public record ImportJobId(UUID value) {

    public ImportJobId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
