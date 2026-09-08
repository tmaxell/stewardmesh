package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "intake_artifact")
class IntakeArtifactEntity {

    @Id
    private UUID id;

    @Column(nullable = false, length = 64, unique = true, updatable = false)
    private String sha256;

    @Column(name = "storage_key", nullable = false, length = 1024, unique = true, updatable = false)
    private String storageKey;

    @Column(name = "content_type", nullable = false, length = 255, updatable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false, updatable = false)
    private long sizeBytes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IntakeArtifactEntity() {}

    private IntakeArtifactEntity(IntakeArtifact artifact) {
        id = artifact.id().value();
        sha256 = artifact.sha256();
        storageKey = artifact.storageKey();
        contentType = artifact.contentType();
        sizeBytes = artifact.sizeBytes();
        createdAt = artifact.createdAt();
    }

    static IntakeArtifactEntity fromDomain(IntakeArtifact artifact) {
        return new IntakeArtifactEntity(artifact);
    }

    IntakeArtifact toDomain() {
        return new IntakeArtifact(
                new IntakeArtifactId(id), sha256, storageKey, contentType, sizeBytes, createdAt);
    }
}
