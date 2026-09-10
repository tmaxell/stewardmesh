package io.stewardmesh.masterdata.domain.goldenrecord;

import java.util.Objects;
import java.util.UUID;

public record SourceAssociationId(UUID value) {

    public SourceAssociationId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
