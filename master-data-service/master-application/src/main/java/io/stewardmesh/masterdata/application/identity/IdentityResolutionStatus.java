package io.stewardmesh.masterdata.application.identity;

import java.time.Instant;
import java.util.Objects;

/** Non-sensitive aggregate status for one match evaluation. */
public record IdentityResolutionStatus(
        IdentityResolutionKey key,
        Instant evaluatedAt,
        int partyCandidates,
        int siteCandidates,
        int autoLinks,
        int reviews,
        int noMatches,
        boolean hardConflict) {

    public IdentityResolutionStatus {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (partyCandidates < 0 || siteCandidates < 0 || autoLinks < 0 || reviews < 0 || noMatches < 0) {
            throw new IllegalArgumentException("identity resolution counts must be non-negative");
        }
        if (autoLinks + reviews + noMatches != partyCandidates + siteCandidates) {
            throw new IllegalArgumentException("identity resolution outcome counts must balance");
        }
    }
}
