package io.stewardmesh.masterdata.application.actionplan;

/** An execution idempotency identity already belongs to different command content. */
public final class ExecutionConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ExecutionConflictException(String message) {
        super(message);
    }

    public ExecutionConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
