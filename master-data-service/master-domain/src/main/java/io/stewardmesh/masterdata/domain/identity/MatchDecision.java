package io.stewardmesh.masterdata.domain.identity;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Reproducible decision for one candidate with its complete feature evidence. */
public record MatchDecision(
        MatchEntityType entityType,
        UUID candidateId,
        MatchOutcome outcome,
        int scoreBasisPoints,
        MatchRulesetId rulesetId,
        boolean hardConflict,
        List<MatchFeature> features) {

    public MatchDecision {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        MatchCandidateSupport.requireScore(scoreBasisPoints);
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        features = MatchCandidateSupport.immutableFeatures(features);
        if (hardConflict && outcome == MatchOutcome.AUTO_LINK) {
            throw new IllegalArgumentException("a hard conflict cannot produce AUTO_LINK");
        }
    }
}
