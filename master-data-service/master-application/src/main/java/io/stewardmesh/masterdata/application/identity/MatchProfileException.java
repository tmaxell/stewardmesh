package io.stewardmesh.masterdata.application.identity;

/** Raised when persistence returns incomplete or inconsistent candidate profiles. */
public final class MatchProfileException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MatchProfileException(String message) {
        super(message);
    }
}
