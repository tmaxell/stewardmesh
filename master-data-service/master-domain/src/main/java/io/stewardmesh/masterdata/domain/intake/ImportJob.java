package io.stewardmesh.masterdata.domain.intake;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Immutable import aggregate; lifecycle changes return a new version. */
public final class ImportJob {

    private static final int MAX_FAILURE_CODE_LENGTH = 128;

    private final ImportJobId id;
    private final IntakeArtifactId artifactId;
    private final SourceSystemRef sourceSystem;
    private final Instant createdAt;
    private final ImportStatus status;
    private final ImportCounters counters;
    private final String failureCode;

    private ImportJob(
            ImportJobId id,
            IntakeArtifactId artifactId,
            SourceSystemRef sourceSystem,
            Instant createdAt,
            ImportStatus status,
            ImportCounters counters,
            String failureCode) {
        this.id = Objects.requireNonNull(id, "id must not be null");
        this.artifactId = Objects.requireNonNull(artifactId, "artifactId must not be null");
        this.sourceSystem = Objects.requireNonNull(sourceSystem, "sourceSystem must not be null");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        this.status = Objects.requireNonNull(status, "status must not be null");
        this.counters = Objects.requireNonNull(counters, "counters must not be null");
        this.failureCode = failureCode;
        if ((status == ImportStatus.FAILED) != (failureCode != null)) {
            throw new IllegalArgumentException("failure code must be present only for failed imports");
        }
    }

    public static ImportJob received(
            ImportJobId id,
            IntakeArtifactId artifactId,
            SourceSystemRef sourceSystem,
            Instant createdAt) {
        return new ImportJob(
                id, artifactId, sourceSystem, createdAt, ImportStatus.RECEIVED, ImportCounters.EMPTY, null);
    }

    public ImportJob startParsing() {
        return transition(ImportStatus.RECEIVED, ImportStatus.PARSING, counters);
    }

    public ImportJob finishParsing(int totalRows) {
        return transition(ImportStatus.PARSING, ImportStatus.PARSED, ImportCounters.parsed(totalRows));
    }

    public ImportJob startValidation() {
        return transition(ImportStatus.PARSED, ImportStatus.VALIDATING, counters);
    }

    public ImportJob finishValidation(
            int acceptedRows, int rejectedRows, int warningCount, int errorCount) {
        return transition(
                ImportStatus.VALIDATING,
                ImportStatus.VALIDATED,
                ImportCounters.validated(
                        counters.totalRows(), acceptedRows, rejectedRows, warningCount, errorCount));
    }

    public ImportJob fail(String code) {
        if (status.isTerminal()) {
            throw new InvalidImportTransitionException(status, ImportStatus.FAILED);
        }
        return new ImportJob(
                id,
                artifactId,
                sourceSystem,
                createdAt,
                ImportStatus.FAILED,
                counters,
                DomainText.requireNonBlank(code, "failure code", MAX_FAILURE_CODE_LENGTH));
    }

    private ImportJob transition(
            ImportStatus requiredStatus, ImportStatus nextStatus, ImportCounters nextCounters) {
        if (status != requiredStatus) {
            throw new InvalidImportTransitionException(status, nextStatus);
        }
        return new ImportJob(
                id, artifactId, sourceSystem, createdAt, nextStatus, nextCounters, null);
    }

    public ImportJobId id() {
        return id;
    }

    public IntakeArtifactId artifactId() {
        return artifactId;
    }

    public SourceSystemRef sourceSystem() {
        return sourceSystem;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public ImportStatus status() {
        return status;
    }

    public ImportCounters counters() {
        return counters;
    }

    public Optional<String> failureCode() {
        return Optional.ofNullable(failureCode);
    }
}
