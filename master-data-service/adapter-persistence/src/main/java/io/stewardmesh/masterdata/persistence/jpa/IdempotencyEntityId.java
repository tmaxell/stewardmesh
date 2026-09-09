package io.stewardmesh.masterdata.persistence.jpa;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
class IdempotencyEntityId implements Serializable {

    private static final long serialVersionUID = 1L;

    @Column(name = "source_system", nullable = false, length = 128)
    private String sourceSystem;

    @Column(name = "idempotency_key", nullable = false, length = 128)
    private String idempotencyKey;

    protected IdempotencyEntityId() {}

    IdempotencyEntityId(String sourceSystem, String idempotencyKey) {
        this.sourceSystem = sourceSystem;
        this.idempotencyKey = idempotencyKey;
    }

    String sourceSystem() {
        return sourceSystem;
    }

    String idempotencyKey() {
        return idempotencyKey;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof IdempotencyEntityId that)) {
            return false;
        }
        return Objects.equals(sourceSystem, that.sourceSystem)
                && Objects.equals(idempotencyKey, that.idempotencyKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sourceSystem, idempotencyKey);
    }
}
