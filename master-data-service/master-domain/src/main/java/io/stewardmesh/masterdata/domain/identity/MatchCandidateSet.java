package io.stewardmesh.masterdata.domain.identity;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** A bounded candidate page with deterministic ordering and explicit truncation evidence. */
public record MatchCandidateSet(
        MatchEntityType entityType,
        int limit,
        boolean truncated,
        List<MatchCandidate> candidates) {

    private static final int MAX_LIMIT = 100;

    public MatchCandidateSet {
        Objects.requireNonNull(entityType, "entityType must not be null");
        if (limit <= 0 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("candidate limit must be between 1 and 100");
        }
        candidates = Objects.requireNonNull(candidates, "candidates must not be null").stream()
                .map(candidate -> Objects.requireNonNull(candidate, "candidate must not be null"))
                .sorted(Comparator.comparingInt(MatchCandidate::scoreBasisPoints)
                        .reversed()
                        .thenComparing(MatchCandidate::candidateId))
                .toList();
        if (candidates.size() > limit) {
            throw new IllegalArgumentException("candidate collection exceeds its declared limit");
        }
        if (truncated && candidates.size() != limit) {
            throw new IllegalArgumentException("a truncated candidate collection must fill its limit");
        }
        if (candidates.stream().anyMatch(candidate -> candidate.entityType() != entityType)) {
            throw new IllegalArgumentException("candidate collection mixes entity types");
        }
        var ids = new HashSet<UUID>();
        if (candidates.stream().map(MatchCandidate::candidateId).anyMatch(id -> !ids.add(id))) {
            throw new IllegalArgumentException("candidate identifiers must be unique");
        }
    }
}
