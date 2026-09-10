package io.stewardmesh.masterdata.domain.goldenrecord;

/** Monotonic version of a reproducible golden-record projection. */
public record GoldenRecordVersion(long value) {

    public GoldenRecordVersion {
        if (value <= 0) {
            throw new IllegalArgumentException("golden record version must be positive");
        }
    }
}
