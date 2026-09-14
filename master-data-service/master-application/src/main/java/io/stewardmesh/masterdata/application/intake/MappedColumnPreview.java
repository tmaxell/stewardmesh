package io.stewardmesh.masterdata.application.intake;

import java.util.Objects;

/** Aggregate completeness for one selected canonical target; source values remain absent. */
public record MappedColumnPreview(
        int sourcePosition,
        String sourceHeader,
        String targetColumn,
        boolean required,
        int nonBlankValues,
        int blankValues,
        int formulaCells) {

    public MappedColumnPreview {
        if (sourcePosition < 1 || nonBlankValues < 0 || blankValues < 0 || formulaCells < 0) {
            throw new IllegalArgumentException("mapped column preview counts are invalid");
        }
        Objects.requireNonNull(sourceHeader, "sourceHeader must not be null");
        Objects.requireNonNull(targetColumn, "targetColumn must not be null");
    }
}
