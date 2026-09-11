package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionStatus;
import java.time.Instant;

public record IdentityResolutionStatusResponse(
        SourceIdentityResponse source,
        String rulesetId,
        Instant evaluatedAt,
        int partyCandidates,
        int siteCandidates,
        int autoLinks,
        int reviews,
        int noMatches,
        boolean hardConflict) {

    static IdentityResolutionStatusResponse from(IdentityResolutionStatus status) {
        return new IdentityResolutionStatusResponse(
                SourceIdentityResponse.from(status.key().sourceRecordIdentity()),
                status.key().rulesetId().value(), status.evaluatedAt(), status.partyCandidates(),
                status.siteCandidates(), status.autoLinks(), status.reviews(), status.noMatches(),
                status.hardConflict());
    }
}
