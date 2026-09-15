package io.stewardmesh.masterdata.application.goldenrecord;

public final class SourceMasterLinkReadException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final boolean ambiguous;

    public SourceMasterLinkReadException(boolean ambiguous) {
        super(ambiguous ? "source has multiple active master links" : "source has no active master link");
        this.ambiguous = ambiguous;
    }

    public boolean ambiguous() {
        return ambiguous;
    }
}
