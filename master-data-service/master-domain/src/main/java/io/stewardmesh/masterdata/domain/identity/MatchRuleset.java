package io.stewardmesh.masterdata.domain.identity;

import java.util.Objects;
import java.util.Set;

/** Versioned deterministic thresholds and hard-conflict policy. */
public record MatchRuleset(
        MatchRulesetId id,
        int autoLinkMinimumBasisPoints,
        int reviewMinimumBasisPoints,
        Set<MatchFeatureCode> hardConflictFeatures) {

    public MatchRuleset {
        Objects.requireNonNull(id, "id must not be null");
        MatchCandidateSupport.requireScore(autoLinkMinimumBasisPoints);
        MatchCandidateSupport.requireScore(reviewMinimumBasisPoints);
        if (reviewMinimumBasisPoints > autoLinkMinimumBasisPoints) {
            throw new IllegalArgumentException("review threshold must not exceed auto-link threshold");
        }
        hardConflictFeatures = Set.copyOf(
                Objects.requireNonNull(hardConflictFeatures, "hardConflictFeatures must not be null"));
    }

    public MatchDecision decide(MatchCandidate candidate) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        boolean hardConflict = candidate.features().stream().anyMatch(feature ->
                feature.signal() == MatchSignal.CONFLICT
                        && hardConflictFeatures.contains(feature.code()));
        MatchOutcome outcome;
        if (hardConflict) {
            outcome = MatchOutcome.REVIEW;
        } else if (candidate.scoreBasisPoints() >= autoLinkMinimumBasisPoints) {
            outcome = MatchOutcome.AUTO_LINK;
        } else if (candidate.scoreBasisPoints() >= reviewMinimumBasisPoints) {
            outcome = MatchOutcome.REVIEW;
        } else {
            outcome = MatchOutcome.NO_MATCH;
        }
        return new MatchDecision(
                candidate.entityType(),
                candidate.candidateId(),
                outcome,
                candidate.scoreBasisPoints(),
                id,
                hardConflict,
                candidate.features());
    }
}
