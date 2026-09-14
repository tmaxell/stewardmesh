package io.stewardmesh.masterdata.application.intake;

import java.util.Objects;
import java.util.Optional;

/** Value-free mapping suggestion for one source header. */
public record SuggestedColumnMapping(
        int sourcePosition,
        String sourceHeader,
        String targetColumn,
        MappingDecision decision,
        int confidenceBasisPoints) {

    public SuggestedColumnMapping {
        if (sourcePosition < 1) {
            throw new IllegalArgumentException("sourcePosition must be positive");
        }
        Objects.requireNonNull(sourceHeader, "sourceHeader must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        if (sourceHeader.isBlank() || confidenceBasisPoints < 0 || confidenceBasisPoints > 10_000) {
            throw new IllegalArgumentException("mapping suggestion is invalid");
        }
        if ((decision == MappingDecision.UNMAPPED) != (targetColumn == null)) {
            throw new IllegalArgumentException("only unmapped suggestions omit a target column");
        }
    }

    public Optional<String> target() {
        return Optional.ofNullable(targetColumn);
    }
}
