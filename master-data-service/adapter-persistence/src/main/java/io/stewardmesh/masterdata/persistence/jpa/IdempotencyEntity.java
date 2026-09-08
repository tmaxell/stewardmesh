package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository.IdempotencyRecord;
import io.stewardmesh.masterdata.domain.intake.IdempotencyKey;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportRequestIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "idempotency_record")
class IdempotencyEntity {

    @EmbeddedId
    private IdempotencyEntityId id;

    @Column(name = "import_job_id", nullable = false, unique = true, updatable = false)
    private UUID importJobId;

    @Column(name = "artifact_sha256", nullable = false, length = 64, updatable = false)
    private String artifactSha256;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected IdempotencyEntity() {}

    private IdempotencyEntity(IdempotencyRecord record) {
        id = new IdempotencyEntityId(
                record.requestIdentity().sourceSystem().value(),
                record.requestIdentity().idempotencyKey().value());
        importJobId = record.importJobId().value();
        artifactSha256 = record.artifactSha256();
        createdAt = record.createdAt();
    }

    static IdempotencyEntity fromDomain(IdempotencyRecord record) {
        return new IdempotencyEntity(record);
    }

    IdempotencyRecord toDomain() {
        return new IdempotencyRecord(
                new ImportRequestIdentity(
                        new SourceSystemRef(id.sourceSystem()), new IdempotencyKey(id.idempotencyKey())),
                new ImportJobId(importJobId),
                artifactSha256,
                createdAt);
    }
}
