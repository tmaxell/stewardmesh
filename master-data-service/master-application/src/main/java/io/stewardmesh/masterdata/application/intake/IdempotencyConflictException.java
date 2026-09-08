package io.stewardmesh.masterdata.application.intake;

/** Raised when one idempotency identity is reused for different workbook content. */
public final class IdempotencyConflictException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public IdempotencyConflictException() {
        super("idempotency identity is already bound to different content");
    }
}
