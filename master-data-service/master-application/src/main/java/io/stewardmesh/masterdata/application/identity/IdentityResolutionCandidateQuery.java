package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import java.util.Objects;

public record IdentityResolutionCandidateQuery(
        IdentityResolutionKey key, MatchEntityType entityType, int page, int size) {

    public static final int MAX_PAGE_SIZE = 100;

    public IdentityResolutionCandidateQuery {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        if (page < 0 || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("candidate pagination is outside the published bounds");
        }
    }
}
