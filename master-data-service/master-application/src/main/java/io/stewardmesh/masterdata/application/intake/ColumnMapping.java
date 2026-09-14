package io.stewardmesh.masterdata.application.intake;

import java.util.Objects;

/** Explicit source-position to canonical-column mapping selected for preview. */
public record ColumnMapping(int sourcePosition, String targetColumn) {

    public ColumnMapping {
        if (sourcePosition < 1) {
            throw new IllegalArgumentException("sourcePosition must be positive");
        }
        Objects.requireNonNull(targetColumn, "targetColumn must not be null");
        if (targetColumn.isBlank()) {
            throw new IllegalArgumentException("targetColumn must not be blank");
        }
    }
}
