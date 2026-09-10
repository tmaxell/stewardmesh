package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

@Embeddable
class GoldenRecordMetadataId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Enumerated(EnumType.STRING)
    @Column(name = "entity_type", nullable = false, length = 8)
    private GoldenEntityType entityType;

    @Column(name = "entity_id", nullable = false)
    private UUID entityId;

    protected GoldenRecordMetadataId() {}

    GoldenRecordMetadataId(GoldenEntityType entityType, UUID entityId) {
        this.entityType = Objects.requireNonNull(entityType, "entityType must not be null");
        this.entityId = Objects.requireNonNull(entityId, "entityId must not be null");
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof GoldenRecordMetadataId that)) {
            return false;
        }
        return entityType == that.entityType && Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(entityType, entityId);
    }
}
