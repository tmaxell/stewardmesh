package io.stewardmesh.masterdata.domain.identity;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

final class MatchCandidateSupport {

    private MatchCandidateSupport() {}

    static int requireScore(int scoreBasisPoints) {
        if (scoreBasisPoints < 0 || scoreBasisPoints > 10_000) {
            throw new IllegalArgumentException("candidate score must be between 0 and 10000");
        }
        return scoreBasisPoints;
    }

    static List<MatchFeature> immutableFeatures(List<MatchFeature> features) {
        var sorted = Objects.requireNonNull(features, "features must not be null").stream()
                .map(feature -> Objects.requireNonNull(feature, "feature must not be null"))
                .sorted(Comparator.comparing(MatchFeature::code))
                .toList();
        if (sorted.isEmpty()) {
            throw new IllegalArgumentException("candidate must contain at least one feature");
        }
        var codes = new HashSet<MatchFeatureCode>();
        if (sorted.stream().map(MatchFeature::code).anyMatch(code -> !codes.add(code))) {
            throw new IllegalArgumentException("candidate feature codes must be unique");
        }
        return sorted;
    }
}
