package io.stewardmesh.masterdata.domain.intake;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/** One immutable assertion from an origin system, retaining original and derived values separately. */
public record SourceRecord(
        SourceRecordIdentity identity,
        ImportJobId importJobId,
        Instant ingestedAt,
        Map<String, String> originalValues,
        Map<String, String> canonicalValues) {

    private static final int MAX_FIELD_NAME_LENGTH = 128;

    public SourceRecord {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");
        originalValues = immutableValues(originalValues, "originalValues");
        canonicalValues = immutableValues(canonicalValues, "canonicalValues");
        if (!originalValues.keySet().containsAll(canonicalValues.keySet())) {
            throw new IllegalArgumentException("canonical values must be derived from original fields");
        }
    }

    private static Map<String, String> immutableValues(Map<String, String> values, String name) {
        var immutable = Map.copyOf(Objects.requireNonNull(values, name + " must not be null"));
        immutable.forEach((field, value) -> {
            DomainText.requireNonBlank(field, "field name", MAX_FIELD_NAME_LENGTH);
            Objects.requireNonNull(value, "source value must not be null");
        });
        return immutable;
    }
}
