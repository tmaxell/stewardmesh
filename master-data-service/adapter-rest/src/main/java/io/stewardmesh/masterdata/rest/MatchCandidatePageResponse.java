package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionCandidatePage;
import java.util.List;

public record MatchCandidatePageResponse(
        SourceIdentityResponse source,
        String rulesetId,
        String entityType,
        int page,
        int size,
        int totalCandidates,
        List<MatchCandidateResponse> candidates) {

    static MatchCandidatePageResponse from(IdentityResolutionCandidatePage result) {
        return new MatchCandidatePageResponse(
                SourceIdentityResponse.from(result.key().sourceRecordIdentity()),
                result.key().rulesetId().value(), result.entityType().name(), result.page(),
                result.size(), result.totalCandidates(), result.candidates().stream()
                        .map(candidate -> MatchCandidateResponse.from(candidate, false)).toList());
    }
}
