package io.stewardmesh.masterdata.domain.intake;

/** Raised when a caller attempts to bypass the import lifecycle. */
public final class InvalidImportTransitionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public InvalidImportTransitionException(ImportStatus current, ImportStatus requested) {
        super("cannot transition import from " + current + " to " + requested);
    }
}
