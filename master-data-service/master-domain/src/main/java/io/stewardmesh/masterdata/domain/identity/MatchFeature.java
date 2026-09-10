package io.stewardmesh.masterdata.domain.identity;

import java.util.Objects;

/** One independently explainable scoring input; it never contains raw supplier values. */
public record MatchFeature(
        MatchFeatureCode code, MatchSignal signal, int contributionBasisPoints) {

    public static final int MIN_CONTRIBUTION = -10_000;
    public static final int MAX_CONTRIBUTION = 10_000;

    public MatchFeature {
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(signal, "signal must not be null");
        if (contributionBasisPoints < MIN_CONTRIBUTION
                || contributionBasisPoints > MAX_CONTRIBUTION) {
            throw new IllegalArgumentException("feature contribution must be between -10000 and 10000");
        }
        if (signal == MatchSignal.CONFLICT && contributionBasisPoints > 0) {
            throw new IllegalArgumentException("a conflict cannot make a positive score contribution");
        }
        if (signal == MatchSignal.MISSING && contributionBasisPoints != 0) {
            throw new IllegalArgumentException("a missing feature must have zero score contribution");
        }
    }
}
