package io.stewardmesh.masterdata.application.intake;

/** Signals that validated source records could not be persisted atomically. */
public class SourceRecordWriteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SourceRecordWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
