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
                        "match_decision",
                        "match_evaluation",
                        "source_record",
                        "stewardship_case",
                        "supplier_party_match_index",
                        "supplier_site_match_index",
                        "validation_issue"),
                tables);
    }

    @Test
    void createsBoundedCandidateLookupIndexes() {
        List<String> indexes = jdbcTemplate().queryForList(
                """
                SELECT indexname
                FROM pg_indexes
                WHERE schemaname = 'public'
                  AND indexname LIKE 'supplier_%_match_%_idx'
                ORDER BY indexname
                """,
                String.class);

        assertEquals(
                List.of(
                        "supplier_party_match_inn_idx",
                        "supplier_party_match_ogrn_idx",
                        "supplier_site_match_address_idx",
                        "supplier_site_match_code_idx",
                        "supplier_site_match_inn_kpp_idx"),
                indexes);
    }

    @Test
    void protectsCandidateIndexIdentifierFormatsAndPartyOwnership() {
        UUID partyId = UUID.randomUUID();
        jdbcTemplate().update(
                "INSERT INTO supplier_party_match_index (party_id, canonical_inn) VALUES (?, ?)",
                partyId,
                "9902000005");

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate().update(
                        "INSERT INTO supplier_party_match_index (party_id, canonical_inn) VALUES (?, ?)",
                        UUID.randomUUID(),
                        "not-an-inn"));
        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate().update(
                        """
                        INSERT INTO supplier_site_match_index
                            (site_id, party_id, canonical_inn, canonical_country_code,
                             canonical_city, canonical_address_line)
                        VALUES (?, ?, ?, ?, ?, ?)
                        """,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "9902000005",
                        "RU",
                        "TEST CITY",
                        "TEST ADDRESS"));
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
    void requiresAnExplicitVersionedNormalizationRuleset() {
        UUID importJobId = insertImportGraph();

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertSourceRecord(importJobId, "record-invalid-ruleset", 1, "supplier-source"));
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
        insertSourceRecord(importJobId, sourceRecordId, version, "supplier-source-v1");
    }

    private static void insertSourceRecord(
            UUID importJobId, String sourceRecordId, long version, String normalizationRuleset) {
        jdbcTemplate().update(
                """
                INSERT INTO source_record
                    (origin_system, source_record_id, source_version, import_job_id,
                     ingested_at, normalization_ruleset,
                     original_values, canonical_values, canonical_inn)
                VALUES (?, ?, ?, ?, ?, ?, '{}'::jsonb, '{}'::jsonb, ?)
                """,
                "SYNTHETIC_ERP",
                sourceRecordId,
                version,
                importJobId,
                Timestamp.from(Instant.parse("2026-08-28T10:16:00Z")),
                normalizationRuleset,
                "9902000005");
    }

    private static String randomChecksum() {
        String random = UUID.randomUUID().toString().replace("-", "");
        return (random + SHA_256).substring(0, 64);
    }
}
