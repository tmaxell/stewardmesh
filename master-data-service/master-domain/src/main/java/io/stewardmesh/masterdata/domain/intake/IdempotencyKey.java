package io.stewardmesh.masterdata.domain.intake;

/** Caller-provided key that identifies one logical import request. */
public record IdempotencyKey(String value) {

    private static final int MAX_LENGTH = 128;

    public IdempotencyKey {
        value = DomainText.requireNonBlank(value, "idempotency key", MAX_LENGTH);
    }
}
