package io.stewardmesh.masterdata.domain.intake;

import java.util.Objects;

/** Immutable source identity; versions are monotonic within an origin record. */
public record SourceRecordIdentity(
        SourceSystemRef originSystem, String sourceRecordId, long sourceVersion) {

    private static final int MAX_SOURCE_RECORD_ID_LENGTH = 128;

    public SourceRecordIdentity {
        Objects.requireNonNull(originSystem, "originSystem must not be null");
        sourceRecordId = DomainText.requireNonBlank(
                sourceRecordId, "source record id", MAX_SOURCE_RECORD_ID_LENGTH);
        if (sourceVersion <= 0) {
            throw new IllegalArgumentException("source version must be positive");
        }
    }
}
