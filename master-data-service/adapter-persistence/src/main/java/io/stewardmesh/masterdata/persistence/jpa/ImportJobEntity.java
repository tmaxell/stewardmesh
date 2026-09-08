package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.intake.ImportCounters;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_job")
class ImportJobEntity {

    @Id
    private UUID id;

    @Column(name = "artifact_id", nullable = false, updatable = false)
    private UUID artifactId;

    @Column(name = "source_system", nullable = false, length = 128, updatable = false)
    private String sourceSystem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ImportStatus status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "accepted_rows", nullable = false)
    private int acceptedRows;

    @Column(name = "rejected_rows", nullable = false)
    private int rejectedRows;

    @Column(name = "warning_count", nullable = false)
    private int warningCount;

    @Column(name = "error_count", nullable = false)
    private int errorCount;

    @Column(name = "failure_code", length = 128)
    private String failureCode;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    @Column(nullable = false)
    private long version;

    protected ImportJobEntity() {}

    private ImportJobEntity(ImportJob importJob) {
        id = importJob.id().value();
        artifactId = importJob.artifactId().value();
        sourceSystem = importJob.sourceSystem().value();
        createdAt = importJob.createdAt();
        apply(importJob);
    }

    static ImportJobEntity fromDomain(ImportJob importJob) {
        return new ImportJobEntity(importJob);
    }

    void apply(ImportJob importJob) {
        if (!id.equals(importJob.id().value())
                || !artifactId.equals(importJob.artifactId().value())
                || !sourceSystem.equals(importJob.sourceSystem().value())
                || !createdAt.equals(importJob.createdAt())) {
            throw new IllegalArgumentException("immutable import identity and lineage cannot change");
        }
        status = importJob.status();
        ImportCounters counters = importJob.counters();
        totalRows = counters.totalRows();
        acceptedRows = counters.acceptedRows();
        rejectedRows = counters.rejectedRows();
        warningCount = counters.warningCount();
        errorCount = counters.errorCount();
        failureCode = importJob.failureCode().orElse(null);
    }

    ImportJob toDomain() {
        return ImportJob.restore(
                new ImportJobId(id),
                new IntakeArtifactId(artifactId),
                new SourceSystemRef(sourceSystem),
                createdAt,
                status,
                new ImportCounters(totalRows, acceptedRows, rejectedRows, warningCount, errorCount),
                failureCode);
    }
}
