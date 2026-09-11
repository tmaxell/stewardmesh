package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import java.util.Objects;
import java.util.UUID;

public record MatchExplanationQuery(
        IdentityResolutionKey key, MatchEntityType entityType, UUID candidateId) {

    public MatchExplanationQuery {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(candidateId, "candidateId must not be null");
    }
}
