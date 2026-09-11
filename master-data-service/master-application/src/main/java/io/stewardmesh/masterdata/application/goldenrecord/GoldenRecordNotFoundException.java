package io.stewardmesh.masterdata.application.goldenrecord;

public final class GoldenRecordNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public GoldenRecordNotFoundException() {
        super("golden record was not found");
    }
}
