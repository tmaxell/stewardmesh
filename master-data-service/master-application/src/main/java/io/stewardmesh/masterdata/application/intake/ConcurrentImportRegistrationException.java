package io.stewardmesh.masterdata.application.intake;

/**
 * Raised when another caller registered the same request identity first.
 *
 * <p>This is not the client's conflict: the losing attempt simply reached the unique identity a
 * moment late. Its transaction rolls back and the operation is retried, and by then the winning
 * record is committed, so the ordinary replay path resolves the request deterministically.
 */
public final class ConcurrentImportRegistrationException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public ConcurrentImportRegistrationException() {
        super("another caller registered this import request identity first");
    }
}
