package io.stewardmesh.masterdata.application.identity;

/** Raised when a candidate blocking adapter violates its bounded result contract. */
public final class CandidateBlockingPortException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public CandidateBlockingPortException(String message) {
        super(message);
    }
}
