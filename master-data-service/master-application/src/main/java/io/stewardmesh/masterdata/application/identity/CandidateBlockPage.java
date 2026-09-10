package io.stewardmesh.masterdata.application.identity;

import java.util.List;
import java.util.Objects;

public record CandidateBlockPage<T>(int limit, boolean truncated, List<T> candidates) {

    public CandidateBlockPage {
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("candidate page limit must be between 1 and 100");
        }
        candidates = List.copyOf(Objects.requireNonNull(candidates, "candidates must not be null"));
        if (candidates.size() > limit) {
            throw new IllegalArgumentException("candidate page exceeds its declared limit");
        }
        if (truncated && candidates.size() != limit) {
            throw new IllegalArgumentException("a truncated candidate page must fill its limit");
        }
    }
}
