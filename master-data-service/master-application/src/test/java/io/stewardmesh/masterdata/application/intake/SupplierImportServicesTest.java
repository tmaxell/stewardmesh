package io.stewardmesh.masterdata.application.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository;
import io.stewardmesh.masterdata.application.port.out.ImportIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.domain.intake.IdempotencyKey;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportRequestIdentity;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class SupplierImportServicesTest {

    private static final Instant NOW = Instant.parse("2026-08-28T10:15:30Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final SourceSystemRef SOURCE = new SourceSystemRef("SYNTHETIC_TEST");

    @Test
    void startsOnceAndReturnsTheExistingImportForAnIdenticalReplay() {
        var state = new State();
        var service = startService(state, "a".repeat(64));
        var command = command("request-1");

        StartSupplierImportResult first = service.execute(command);
        StartSupplierImportResult replay = service.execute(command);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.importJobId(), replay.importJobId());
        assertEquals(1, state.jobs.size());
        assertEquals(1, state.artifacts.size());
        assertEquals(1, state.idempotency.size());
    }

    @Test
    void rejectsAnIdempotencyKeyReusedForDifferentContent() {
        var state = new State();
        startService(state, "a".repeat(64)).execute(command("request-2"));

        var conflict = startService(state, "b".repeat(64));

        assertThrows(IdempotencyConflictException.class, () -> conflict.execute(command("request-2")));
        assertEquals(1, state.jobs.size());
        assertEquals(1, state.artifacts.size());
    }

    @Test
    void processesRowsAndPersistsValidationEvidenceWithTheFinalState() {
        var state = new State();
        ImportJob job = receivedJob();
        state.jobs.put(job.id(), job);
        var sourceRecord = new SourceRecord(
                new SourceRecordIdentity(SOURCE, "record-1", 1),
                job.id(),
                NOW,
                Map.of("legal_name", " Synthetic Supplier "),
                Map.of("legal_name", "Synthetic Supplier"));
        var issue = new ValidationIssue(
                ValidationCode.REQUIRED_VALUE_MISSING, 3, "legal_name", Map.of());
        var writtenRecords = new ArrayList<SourceRecord>();
        var writtenIssues = new ArrayList<ValidationIssue>();
        var service = new ProcessSupplierImportService(
                state,
                ignored -> content(),
                ignored -> new SupplierWorkbookParseResult(2, List.of(sourceRecord), List.of(issue)),
                (ignored, records, issues) -> {
                    writtenRecords.addAll(records);
                    writtenIssues.addAll(issues);
                },
                new DirectTransaction(),
                CLOCK);

        ProcessSupplierImportResult result = service.execute(job.id());

        assertEquals(ImportStatus.VALIDATED, result.status());
        assertEquals(2, result.counters().totalRows());
        assertEquals(1, result.counters().acceptedRows());
        assertEquals(1, result.counters().rejectedRows());
        assertEquals(1, result.counters().errorCount());
        assertEquals(List.of(sourceRecord), writtenRecords);
        assertEquals(List.of(issue), writtenIssues);
        assertEquals(ImportStatus.VALIDATED, state.jobs.get(job.id()).status());
    }

    @Test
    void marksStructurallyInvalidWorkbooksAsFailedAndRetainsTheirEvidence() {
        var state = new State();
        ImportJob job = receivedJob();
        state.jobs.put(job.id(), job);
        var issue = new ValidationIssue(
                ValidationCode.WORKBOOK_UNSAFE_CONTENT, null, null, Map.of());
        var writtenIssues = new ArrayList<ValidationIssue>();
        var service = new ProcessSupplierImportService(
                state,
                ignored -> content(),
                ignored -> new SupplierWorkbookParseResult(0, List.of(), List.of(issue)),
                (ignored, records, issues) -> writtenIssues.addAll(issues),
                new DirectTransaction(),
                CLOCK);

        ProcessSupplierImportResult result = service.execute(job.id());

        assertEquals(ImportStatus.FAILED, result.status());
        assertEquals("WORKBOOK_VALIDATION_FAILED", state.jobs.get(job.id()).failureCode().orElseThrow());
        assertEquals(List.of(issue), writtenIssues);
    }

    @Test
    void exposesArtifactReadFailureAsAStableTerminalState() {
        var state = new State();
        ImportJob job = receivedJob();
        state.jobs.put(job.id(), job);
        var service = new ProcessSupplierImportService(
                state,
                ignored -> {
                    throw new IntakeArtifactAccessException("synthetic storage outage");
                },
                ignored -> new SupplierWorkbookParseResult(0, List.of(), List.of()),
                (ignored, records, issues) -> {},
                new DirectTransaction(),
                CLOCK);

        ProcessSupplierImportResult result = service.execute(job.id());

        assertEquals(ImportStatus.FAILED, result.status());
        assertEquals(
                "ARTIFACT_STORAGE_UNAVAILABLE",
                state.jobs.get(job.id()).failureCode().orElseThrow());
    }

    @Test
    void exposesSourceRecordWriteFailureAfterRollingBackTheBatch() {
        var state = new State();
        ImportJob job = receivedJob();
        state.jobs.put(job.id(), job);
        var service = new ProcessSupplierImportService(
                state,
                ignored -> content(),
                ignored -> new SupplierWorkbookParseResult(1, List.of(), List.of()),
                (ignored, records, issues) -> {
                    throw new SourceRecordWriteException(
                            "synthetic persistence failure", new IllegalStateException("test"));
                },
                new DirectTransaction(),
                CLOCK);

        ProcessSupplierImportResult result = service.execute(job.id());

        assertEquals(ImportStatus.FAILED, result.status());
        assertEquals(
                "SOURCE_RECORD_PERSISTENCE_FAILED",
                state.jobs.get(job.id()).failureCode().orElseThrow());
    }

    @Test
    void statusAndReportReadersRejectUnknownImports() {
        var state = new State();
        ImportJobId missing = new ImportJobId(UUID.randomUUID());

        assertThrows(
                SupplierImportNotFoundException.class,
                () -> new GetSupplierImportStatusService(state).execute(missing));
        assertThrows(
                SupplierImportNotFoundException.class,
                () -> new GetSupplierImportReportService(
                                state,
                                query -> new SupplierImportReport(
                                        query.importJobId(), query.page(), query.size(), 0, List.of()))
                        .execute(new SupplierImportReportQuery(missing, 0, 20)));
    }

    private static StartSupplierImportService startService(State state, String checksum) {
        return new StartSupplierImportService(
                ignored -> new IntakeArtifact(
                        new IntakeArtifactId(UUID.randomUUID()),
                        checksum,
                        "intake/sha256/" + checksum,
                        content().contentType(),
                        content().sizeBytes(),
                        NOW),
                state,
                state,
                state,
                new Identities(),
                new DirectTransaction(),
                CLOCK);
    }

    private static StartSupplierImportCommand command(String key) {
        return new StartSupplierImportCommand(
                new ImportRequestIdentity(SOURCE, new IdempotencyKey(key)), content());
    }

    private static ImportJob receivedJob() {
        return ImportJob.received(
                new ImportJobId(UUID.randomUUID()),
                new IntakeArtifactId(UUID.randomUUID()),
                SOURCE,
                NOW);
    }

    private static IntakeContent content() {
        byte[] bytes = {0x50, 0x4b, 0x03, 0x04};
        return new IntakeContent() {
            @Override
            public String contentType() {
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            }

            @Override
            public long sizeBytes() {
                return bytes.length;
            }

            @Override
            public InputStream openStream() {
                return new ByteArrayInputStream(bytes);
            }
        };
    }

    private static final class DirectTransaction implements ApplicationTransaction {

        @Override
        public <T> T execute(Supplier<T> operation) {
            return operation.get();
        }
    }

    private static final class Identities implements ImportIdentityGenerator {

        @Override
        public ImportJobId nextImportJobId() {
            return new ImportJobId(UUID.randomUUID());
        }

        @Override
        public IntakeArtifactId nextArtifactId() {
            return new IntakeArtifactId(UUID.randomUUID());
        }
    }

    private static final class State
            implements IntakeArtifactRepository, ImportJobRepository, IdempotencyRepository {

        private final Map<String, IntakeArtifact> artifacts = new HashMap<>();
        private final Map<ImportJobId, ImportJob> jobs = new HashMap<>();
        private final Map<ImportRequestIdentity, IdempotencyRecord> idempotency = new HashMap<>();

        @Override
        public Optional<IntakeArtifact> findById(IntakeArtifactId artifactId) {
            return artifacts.values().stream()
                    .filter(artifact -> artifact.id().equals(artifactId))
                    .findFirst();
        }

        @Override
        public Optional<IntakeArtifact> findBySha256(String sha256) {
            return Optional.ofNullable(artifacts.get(sha256));
        }

        @Override
        public void save(IntakeArtifact artifact) {
            artifacts.putIfAbsent(artifact.sha256(), artifact);
        }

        @Override
        public Optional<ImportJob> findById(ImportJobId importJobId) {
            return Optional.ofNullable(jobs.get(importJobId));
        }

        @Override
        public void save(ImportJob importJob) {
            jobs.put(importJob.id(), importJob);
        }

        @Override
        public Optional<IdempotencyRecord> find(ImportRequestIdentity requestIdentity) {
            return Optional.ofNullable(idempotency.get(requestIdentity));
        }

        @Override
        public void save(IdempotencyRecord record) {
            idempotency.putIfAbsent(record.requestIdentity(), record);
        }
    }
}
