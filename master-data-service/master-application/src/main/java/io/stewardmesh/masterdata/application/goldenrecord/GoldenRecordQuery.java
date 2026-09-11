package io.stewardmesh.masterdata.application.goldenrecord;

import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import java.util.Objects;
import java.util.UUID;

public record GoldenRecordQuery(GoldenEntityType entityType, UUID entityId) {

    public GoldenRecordQuery {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
    }
}
