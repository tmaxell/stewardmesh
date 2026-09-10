package io.stewardmesh.masterdata.application.identity;

public record CandidateBlockingPolicy(int maximumCandidates) {

    private static final int HARD_MAXIMUM = 100;

    public CandidateBlockingPolicy {
        if (maximumCandidates <= 0 || maximumCandidates > HARD_MAXIMUM) {
            throw new IllegalArgumentException("maximum candidates must be between 1 and 100");
        }
    }

    public static CandidateBlockingPolicy conservativeDefault() {
        return new CandidateBlockingPolicy(50);
    }

    public void requireAllowed(int requestedLimit) {
        if (requestedLimit <= 0 || requestedLimit > maximumCandidates) {
            throw new IllegalArgumentException("requested candidate limit exceeds policy");
        }
    }
}
