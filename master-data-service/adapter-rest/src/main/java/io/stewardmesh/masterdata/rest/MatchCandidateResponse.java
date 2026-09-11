package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import java.util.List;
import java.util.UUID;

public record MatchCandidateResponse(
        String entityType,
        UUID candidateId,
        String outcome,
        int scoreBasisPoints,
        String rulesetId,
        boolean hardConflict,
        List<MatchFeatureResponse> features) {

    static MatchCandidateResponse from(MatchDecision decision, boolean includeFeatures) {
        return new MatchCandidateResponse(
                decision.entityType().name(), decision.candidateId(), decision.outcome().name(),
                decision.scoreBasisPoints(), decision.rulesetId().value(), decision.hardConflict(),
                includeFeatures
                        ? decision.features().stream().map(MatchFeatureResponse::from).toList()
                        : List.of());
    }
}
