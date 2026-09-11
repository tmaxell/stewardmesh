package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import java.util.List;
import java.util.Objects;

public record IdentityResolutionCandidatePage(
        IdentityResolutionKey key,
        MatchEntityType entityType,
        int page,
        int size,
        int totalCandidates,
        List<MatchDecision> candidates) {

    public IdentityResolutionCandidatePage {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        if (page < 0 || size < 1 || size > IdentityResolutionCandidateQuery.MAX_PAGE_SIZE
                || totalCandidates < candidates.size() || candidates.size() > size) {
            throw new IllegalArgumentException("candidate page violates its published bounds");
        }
        if (candidates.stream().anyMatch(candidate -> candidate.entityType() != entityType)) {
            throw new IllegalArgumentException("candidate page mixes entity types");
        }
    }
}
