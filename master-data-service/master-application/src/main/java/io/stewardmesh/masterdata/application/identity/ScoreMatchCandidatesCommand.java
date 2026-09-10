package io.stewardmesh.masterdata.application.identity;

import java.util.Objects;

public record ScoreMatchCandidatesCommand(MatchCandidateBlocks candidateBlocks) {

    public ScoreMatchCandidatesCommand {
        Objects.requireNonNull(candidateBlocks, "candidateBlocks must not be null");
    }
}
