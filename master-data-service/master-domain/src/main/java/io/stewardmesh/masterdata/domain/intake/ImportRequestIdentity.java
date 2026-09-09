package io.stewardmesh.masterdata.domain.intake;

import java.util.Objects;

/** Idempotency scope for one origin system and one caller-provided request key. */
public record ImportRequestIdentity(SourceSystemRef sourceSystem, IdempotencyKey idempotencyKey) {

    public ImportRequestIdentity {
        Objects.requireNonNull(sourceSystem, "sourceSystem must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey must not be null");
    }
}
