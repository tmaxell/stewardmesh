package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.port.out.LoadSourceRecord;
import io.stewardmesh.masterdata.domain.identity.NormalizationRulesetId;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Restores immutable source assertions for deterministic candidate generation. */
public final class JdbcSourceRecordLoader implements LoadSourceRecord {

    private static final TypeReference<Map<String, String>> SOURCE_VALUES_TYPE =
            new TypeReference<>() {};

    private static final String FIND_SOURCE_RECORD = """
            SELECT import_job_id,
                   ingested_at,
                   normalization_ruleset,
                   original_values::text AS original_values,
                   canonical_values::text AS canonical_values
            FROM source_record
            WHERE origin_system = ?
              AND source_record_id = ?
              AND source_version = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSourceRecordLoader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<SourceRecord> findByIdentity(SourceRecordIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        return jdbcTemplate
                .query(
                        FIND_SOURCE_RECORD,
                        (resultSet, rowNumber) -> new SourceRecord(
                                identity,
                                new ImportJobId(resultSet.getObject("import_job_id", java.util.UUID.class)),
                                resultSet.getTimestamp("ingested_at").toInstant(),
                                new NormalizationRulesetId(resultSet.getString("normalization_ruleset")),
                                readValues(resultSet.getString("original_values")),
                                readValues(resultSet.getString("canonical_values"))),
                        identity.originSystem().value(),
                        identity.sourceRecordId(),
                        identity.sourceVersion())
                .stream()
                .findFirst();
    }

    private Map<String, String> readValues(String json) {
        try {
            return objectMapper.readValue(json, SOURCE_VALUES_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored source record values are invalid", exception);
        }
    }
}
