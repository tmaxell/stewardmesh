package io.stewardmesh.masterdata.application.goldenrecord;

public final class GoldenRecordWriteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GoldenRecordWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
