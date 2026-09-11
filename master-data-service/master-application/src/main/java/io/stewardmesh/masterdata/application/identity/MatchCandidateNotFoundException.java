package io.stewardmesh.masterdata.application.identity;

public final class MatchCandidateNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public MatchCandidateNotFoundException() {
        super("match candidate was not found");
    }
}
