package io.stewardmesh.masterdata.domain.goldenrecord;

/** Explicit source trust order. Higher values win before recency and completeness. */
public record SourcePriority(int value) {

    public static final int MIN_VALUE = 0;
    public static final int MAX_VALUE = 1_000;

    public SourcePriority {
        if (value < MIN_VALUE || value > MAX_VALUE) {
            throw new IllegalArgumentException("source priority must be between 0 and 1000");
        }
    }
}
