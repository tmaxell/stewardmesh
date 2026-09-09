package io.stewardmesh.masterdata.domain.intake;

import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/** Metadata for original workbook bytes stored under an immutable content-addressed key. */
public record IntakeArtifact(
        IntakeArtifactId id,
        String sha256,
        String storageKey,
        String contentType,
        long sizeBytes,
        Instant createdAt) {

    private static final Pattern SHA_256 = Pattern.compile("^[0-9a-f]{64}$");

    public IntakeArtifact {
        Objects.requireNonNull(id, "id must not be null");
        if (!SHA_256.matcher(Objects.requireNonNull(sha256, "sha256 must not be null")).matches()) {
            throw new IllegalArgumentException("sha256 must be 64 lowercase hexadecimal characters");
        }
        storageKey = DomainText.requireNonBlank(storageKey, "storage key", 1024);
        contentType = DomainText.requireNonBlank(contentType, "content type", 255);
        if (sizeBytes < 0) {
            throw new IllegalArgumentException("artifact size must not be negative");
        }
        Objects.requireNonNull(createdAt, "createdAt must not be null");
    }
}
