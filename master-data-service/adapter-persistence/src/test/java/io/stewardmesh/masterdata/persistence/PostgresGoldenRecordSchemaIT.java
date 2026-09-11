package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.UncategorizedSQLException;

class PostgresGoldenRecordSchemaIT extends PostgreSqlIntegrationTestSupport {

    private static final Instant PROJECTED_AT = Instant.parse("2026-09-11T10:00:00Z");

    @Test
    void requiresCompleteAttributeProvenance() {
        UUID partyId = UUID.randomUUID();
        insertPartyMetadataAndVersion(partyId, 1);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> jdbcTemplate().update(
                        """
                        INSERT INTO golden_attribute
                            (entity_type, entity_id, projection_version, attribute_name,
                             attribute_value, origin_system, source_record_id, source_version,
                             association_id, survivorship_rule, survivorship_ruleset, decided_at)
                        VALUES ('PARTY', ?, 1, 'LEGAL_NAME', ?, ?, ?, 1,
                                NULL, 'TRUSTED_SOURCE', 'supplier-survivorship-v1', ?)
                        """,
                        partyId,
                        "SYNTHETIC PARTY",
                        "SYNTHETIC_SCHEMA",
                        "missing-source",
                        Timestamp.from(PROJECTED_AT)));
    }

    @Test
    void preservesImmutableVersionsAndRejectsDuplicates() {
        UUID partyId = UUID.randomUUID();
        insertPartyMetadataAndVersion(partyId, 1);

        assertThrows(
                DataIntegrityViolationException.class,
                () -> insertPartyVersion(partyId, 1));
        var updateFailure = assertThrows(
                UncategorizedSQLException.class,
                () -> jdbcTemplate().update(
                        """
                        UPDATE golden_record_version SET projection_version = 2
                        WHERE entity_type = 'PARTY' AND entity_id = ?
                        """,
                        partyId));
        assertTrue(updateFailure.getMessage().contains("immutable intake row"));
    }

    @Test
    void enforcesOptimisticMetadataVersionColumn() {
        UUID partyId = UUID.randomUUID();
        insertPartyMetadataAndVersion(partyId, 1);

        int first = jdbcTemplate().update(
                """
                UPDATE golden_record_metadata
                SET projection_version = 2, lock_version = lock_version + 1
                WHERE entity_type = 'PARTY' AND entity_id = ? AND lock_version = 0
                """,
                partyId);
        int stale = jdbcTemplate().update(
                """
                UPDATE golden_record_metadata
                SET projection_version = 3, lock_version = lock_version + 1
                WHERE entity_type = 'PARTY' AND entity_id = ? AND lock_version = 0
                """,
                partyId);

        assertEquals(1, first);
        assertEquals(0, stale);
    }

    private static void insertPartyMetadataAndVersion(UUID partyId, long version) {
        jdbcTemplate().update(
                """
                INSERT INTO golden_record_metadata
                    (entity_type, entity_id, party_id, projection_version,
                     survivorship_ruleset, projected_at)
                VALUES ('PARTY', ?, ?, ?, 'supplier-survivorship-v1', ?)
                """,
                partyId,
                partyId,
                version,
                Timestamp.from(PROJECTED_AT));
        insertPartyVersion(partyId, version);
    }

    private static void insertPartyVersion(UUID partyId, long version) {
        jdbcTemplate().update(
                """
                INSERT INTO golden_record_version
                    (entity_type, entity_id, projection_version, party_id,
                     survivorship_ruleset, projected_at)
                VALUES ('PARTY', ?, ?, ?, 'supplier-survivorship-v1', ?)
                """,
                partyId,
                version,
                partyId,
                Timestamp.from(PROJECTED_AT));
    }
}
