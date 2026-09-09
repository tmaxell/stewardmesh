package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.intake.IdempotencyConflictException;
import io.stewardmesh.masterdata.application.intake.SourceRecordWriteException;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository;
import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository.IdempotencyRecord;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.application.port.out.SourceRecordWriter;
import io.stewardmesh.masterdata.application.port.out.ValidationIssueReader;
import io.stewardmesh.masterdata.domain.intake.IdempotencyKey;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportRequestIdentity;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import io.stewardmesh.masterdata.persistence.jpa.IntakePersistenceConfiguration;
import java.time.Instant;
import java.util.UUID;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = JpaIntakeMetadataIT.TestApplication.class)
class JpaIntakeMetadataIT extends PostgreSqlIntegrationTestSupport {

    private static final Instant CREATED_AT = Instant.parse("2026-08-28T10:15:30Z");

    @Autowired
    private IntakeArtifactRepository artifactRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private IdempotencyRepository idempotencyRepository;

    @Autowired
    private SourceRecordWriter sourceRecordWriter;

    @Autowired
    private ValidationIssueReader validationIssueReader;

    @Autowired
    private ApplicationTransaction applicationTransaction;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void roundTripsImmutableArtifactAndImportLifecycleMetadata() {
        IntakeArtifact artifact = artifact();
        artifactRepository.save(artifact);
        ImportJob received = ImportJob.received(
                importJobId(), artifact.id(), new SourceSystemRef("SYNTHETIC_JPA"), CREATED_AT);
        importJobRepository.save(received);
        importJobRepository.save(received.startParsing().finishParsing(3));

        assertEquals(artifact, artifactRepository.findBySha256(artifact.sha256()).orElseThrow());
        var restored = importJobRepository.findById(received.id()).orElseThrow();
        assertEquals(ImportStatus.PARSED, restored.status());
        assertEquals(3, restored.counters().totalRows());
    }

    @Test
    void treatsTheSameIdempotencyRecordAsReplayAndRejectsDifferentContent() {
        IntakeArtifact artifact = artifact();
        artifactRepository.save(artifact);
        ImportJob job = ImportJob.received(
                importJobId(), artifact.id(), new SourceSystemRef("SYNTHETIC_IDEMPOTENCY"), CREATED_AT);
        importJobRepository.save(job);
        var identity = new ImportRequestIdentity(
                job.sourceSystem(), new IdempotencyKey("metadata-request-1"));
        var first = new IdempotencyRecord(identity, job.id(), artifact.sha256(), CREATED_AT);

        idempotencyRepository.save(first);
        idempotencyRepository.save(first);

        assertEquals(first, idempotencyRepository.find(identity).orElseThrow());
        var conflict = new IdempotencyRecord(identity, job.id(), "b".repeat(64), CREATED_AT);
        assertThrows(IdempotencyConflictException.class, () -> idempotencyRepository.save(conflict));
    }

    @Test
    void writesSourceRowsAndReadsValidationIssuesInStableOrder() {
        ImportJob job = persistedJob("SYNTHETIC_BATCH");
        SourceRecord sourceRecord = sourceRecord(job, "batch-record-1", 1);
        List<ValidationIssue> issues = List.of(
                new ValidationIssue(
                        ValidationCode.HEADER_UNKNOWN, 1, "legacy_note", Map.of("header", "legacy_note")),
                new ValidationIssue(
                        ValidationCode.REQUIRED_VALUE_MISSING, 2, "legal_name", Map.of()));

        sourceRecordWriter.writeBatch(job.id(), List.of(sourceRecord), issues);
        var report = validationIssueReader.read(
                new io.stewardmesh.masterdata.application.intake.SupplierImportReportQuery(
                        job.id(), 0, 100));

        assertEquals(List.of(ValidationCode.HEADER_UNKNOWN, ValidationCode.REQUIRED_VALUE_MISSING),
                report.issues().stream().map(ValidationIssue::code).toList());
        assertEquals(2, report.totalIssues());
        assertEquals("9902000005", jdbcTemplate.queryForObject(
                "SELECT canonical_inn FROM source_record WHERE import_job_id = ?",
                String.class,
                job.id().value()));
    }

    @Test
    void rollsBackTheWholeBatchWhenOneSourceIdentityIsDuplicated() {
        ImportJob job = persistedJob("SYNTHETIC_ROLLBACK");
        SourceRecord duplicate = sourceRecord(job, "duplicate-record", 1);

        assertThrows(
                SourceRecordWriteException.class,
                () -> sourceRecordWriter.writeBatch(job.id(), List.of(duplicate, duplicate), List.of()));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM source_record WHERE import_job_id = ?",
                Integer.class,
                job.id().value()));
    }

    @Test
    void rollsBackAllRepositoriesInsideTheApplicationTransaction() {
        IntakeArtifact artifact = artifact();
        ImportJob job = ImportJob.received(
                importJobId(), artifact.id(), new SourceSystemRef("SYNTHETIC_TRANSACTION"), CREATED_AT);

        assertThrows(IllegalStateException.class, () -> applicationTransaction.execute(() -> {
            artifactRepository.save(artifact);
            importJobRepository.save(job);
            throw new IllegalStateException("synthetic rollback trigger");
        }));

        assertFalse(artifactRepository.findById(artifact.id()).isPresent());
        assertFalse(importJobRepository.findById(job.id()).isPresent());
    }

    private static IntakeArtifact artifact() {
        UUID id = UUID.randomUUID();
        String sha256 = (id.toString().replace("-", "") + "a".repeat(64)).substring(0, 64);
        return new IntakeArtifact(
                new IntakeArtifactId(id),
                sha256,
                "intake/sha256/" + sha256,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                256,
                CREATED_AT);
    }

    private static ImportJobId importJobId() {
        return new ImportJobId(UUID.randomUUID());
    }

    private ImportJob persistedJob(String sourceSystem) {
        IntakeArtifact artifact = artifact();
        artifactRepository.save(artifact);
        ImportJob job = ImportJob.received(
                importJobId(), artifact.id(), new SourceSystemRef(sourceSystem), CREATED_AT);
        importJobRepository.save(job);
        return job;
    }

    private static SourceRecord sourceRecord(ImportJob job, String sourceRecordId, long sourceVersion) {
        return new SourceRecord(
                new SourceRecordIdentity(job.sourceSystem(), sourceRecordId, sourceVersion),
                job.id(),
                CREATED_AT.plusSeconds(30),
                Map.of("legal_name", "  Synthetic Supplier  ", "inn", "9902000005"),
                Map.of("legal_name", "SYNTHETIC SUPPLIER", "inn", "9902000005"));
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(IntakePersistenceConfiguration.class)
    static class TestApplication {}
}
