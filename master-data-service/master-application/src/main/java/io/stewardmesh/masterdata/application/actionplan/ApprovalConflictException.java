package io.stewardmesh.masterdata.application.actionplan;

/** An idempotency key or plan already belongs to a different immutable decision. */
public final class ApprovalConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ApprovalConflictException(String message) {
        super(message);
    }

    public ApprovalConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
