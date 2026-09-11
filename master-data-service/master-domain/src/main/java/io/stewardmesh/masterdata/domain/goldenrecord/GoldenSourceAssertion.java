package io.stewardmesh.masterdata.domain.goldenrecord;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.time.Instant;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Typed canonical values from one immutable source assertion admitted to mastering. */
public record GoldenSourceAssertion(
        SourceRecordIdentity sourceRecord,
        Instant ingestedAt,
        SourcePriority priority,
        SourceAssociation association,
        Map<GoldenAttributeName, String> canonicalValues) {

    public GoldenSourceAssertion {
        Objects.requireNonNull(sourceRecord, "sourceRecord must not be null");
        Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");
        Objects.requireNonNull(priority, "priority must not be null");
        Objects.requireNonNull(association, "association must not be null");
        if (!association.sourceRecord().equals(sourceRecord)) {
            throw new IllegalArgumentException("association must identify the same source record");
        }
        var copy = new EnumMap<GoldenAttributeName, String>(GoldenAttributeName.class);
        Objects.requireNonNull(canonicalValues, "canonicalValues must not be null")
                .forEach((name, value) -> {
                    Objects.requireNonNull(name, "canonical attribute name must not be null");
                    if (value != null && !value.isBlank()) {
                        copy.put(name, value.trim());
                    }
                });
        canonicalValues = Collections.unmodifiableMap(copy);
    }

    public int completeness() {
        return canonicalValues.size();
    }
}
