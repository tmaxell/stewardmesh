package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;

class PostgresIntakeSchemaIT extends PostgreSqlIntegrationTestSupport {

    private static final String SHA_256 = "a".repeat(64);

    @Test
    void appliesTheCompleteInitialMigrationToAnEmptyPostgresDatabase() {
        List<String> tables = jdbcTemplate().queryForList(
                """
                SELECT table_name
                FROM information_schema.tables
                WHERE table_schema = 'public'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """,
                String.class);

        assertEquals(
                List.of(
                        "idempotency_record",
                        "import_job",
                        "intake_artifact",
                        "source_record",
                        "validation_issue"),
                tables);
    }

    @Test
    void enforcesSourceIdentityVersionUniqueness() {
        UUID importJobId = insertImportGraph();
        insertSourceRecord(importJobId, "record-1", 1);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertSourceRecord(importJobId, "record-1", 1));
    }

    @Test
    void preventsChangingOrDeletingImmutableSourceAssertions() {
        UUID importJobId = insertImportGraph();
        insertSourceRecord(importJobId, "record-immutable", 1);

        var updateFailure = assertThrows(
                UncategorizedSQLException.class,
                () -> jdbcTemplate().update(
                        """
                        UPDATE source_record SET canonical_values = '{}'::jsonb
                        WHERE origin_system = ? AND source_record_id = ? AND source_version = ?
                        """,
                        "SYNTHETIC_ERP",
                        "record-immutable",
                        1));
        assertTrue(updateFailure.getMessage().contains("immutable intake row"));
        assertThrows(
                UncategorizedSQLException.class,
                () -> jdbcTemplate().update(
                        """
                        DELETE FROM source_record
                        WHERE origin_system = ? AND source_record_id = ? AND source_version = ?
                        """,
                        "SYNTHETIC_ERP",
                        "record-immutable",
                        1));
    }

    private static UUID insertImportGraph() {
        UUID artifactId = UUID.randomUUID();
        UUID importJobId = UUID.randomUUID();
        jdbcTemplate().update(
                """
                INSERT INTO intake_artifact
                    (id, sha256, storage_key, content_type, size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                artifactId,
                randomChecksum(),
                "intake/sha256/" + artifactId,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                128L,
                Timestamp.from(Instant.parse("2026-08-28T10:15:30Z")));
        jdbcTemplate().update(
                """
                INSERT INTO import_job
                    (id, artifact_id, source_system, status, created_at)
                VALUES (?, ?, ?, ?, ?)
                """,
                importJobId,
                artifactId,
                "SYNTHETIC_ERP",
                "RECEIVED",
                Timestamp.from(Instant.parse("2026-08-28T10:15:30Z")));
        return importJobId;
    }

    private static void insertSourceRecord(UUID importJobId, String sourceRecordId, long version) {
        jdbcTemplate().update(
                """
                INSERT INTO source_record
                    (origin_system, source_record_id, source_version, import_job_id,
                     ingested_at, original_values, canonical_values, canonical_inn)
                VALUES (?, ?, ?, ?, ?, '{}'::jsonb, '{}'::jsonb, ?)
                """,
                "SYNTHETIC_ERP",
                sourceRecordId,
                version,
                importJobId,
                Timestamp.from(Instant.parse("2026-08-28T10:16:00Z")),
                "9902000005");
    }

    private static String randomChecksum() {
        String random = UUID.randomUUID().toString().replace("-", "");
        return (random + SHA_256).substring(0, 64);
    }
}
