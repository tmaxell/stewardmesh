package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.intake.SourceRecordWriteException;
import io.stewardmesh.masterdata.application.port.out.SourceRecordWriter;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class JdbcSourceRecordWriter implements SourceRecordWriter {

    private static final String INSERT_SOURCE_RECORD = """
            INSERT INTO source_record
                (origin_system, source_record_id, source_version, import_job_id, ingested_at,
                 original_values, canonical_values, canonical_inn, canonical_kpp, canonical_ogrn)
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, ?, ?)
            """;
    private static final String INSERT_VALIDATION_ISSUE = """
            INSERT INTO validation_issue
                (import_job_id, issue_index, code, row_number, field_name, parameters)
            VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSourceRecordWriter(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional
    public void writeBatch(
            ImportJobId importJobId,
            List<SourceRecord> sourceRecords,
            List<ValidationIssue> validationIssues) {
        var records = List.copyOf(sourceRecords);
        var issues = List.copyOf(validationIssues);
        if (records.stream().anyMatch(record -> !record.importJobId().equals(importJobId))) {
            throw new IllegalArgumentException("every source record must belong to the requested import");
        }
        try {
            jdbcTemplate.batchUpdate(INSERT_SOURCE_RECORD, new SourceRecordBatch(records));
            jdbcTemplate.batchUpdate(
                    INSERT_VALIDATION_ISSUE, new ValidationIssueBatch(importJobId, issues));
        } catch (DataAccessException exception) {
            throw new SourceRecordWriteException("source record batch could not be persisted", exception);
        }
    }

    private String json(Map<String, String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("intake values cannot be serialized", exception);
        }
    }

    private final class SourceRecordBatch implements BatchPreparedStatementSetter {

        private final List<SourceRecord> records;

        private SourceRecordBatch(List<SourceRecord> records) {
            this.records = records;
        }

        @Override
        public void setValues(PreparedStatement statement, int index) throws SQLException {
            SourceRecord record = records.get(index);
            statement.setString(1, record.identity().originSystem().value());
            statement.setString(2, record.identity().sourceRecordId());
            statement.setLong(3, record.identity().sourceVersion());
            statement.setObject(4, record.importJobId().value());
            statement.setTimestamp(5, Timestamp.from(record.ingestedAt()));
            statement.setString(6, json(record.originalValues()));
            statement.setString(7, json(record.canonicalValues()));
            statement.setString(8, record.canonicalValues().get("inn"));
            statement.setString(9, record.canonicalValues().get("kpp"));
            statement.setString(10, record.canonicalValues().get("ogrn"));
        }

        @Override
        public int getBatchSize() {
            return records.size();
        }
    }

    private final class ValidationIssueBatch implements BatchPreparedStatementSetter {

        private final ImportJobId importJobId;
        private final List<ValidationIssue> issues;

        private ValidationIssueBatch(ImportJobId importJobId, List<ValidationIssue> issues) {
            this.importJobId = importJobId;
            this.issues = issues;
        }

        @Override
        public void setValues(PreparedStatement statement, int index) throws SQLException {
            ValidationIssue issue = issues.get(index);
            statement.setObject(1, importJobId.value());
            statement.setInt(2, index);
            statement.setString(3, issue.code().name());
            if (issue.rowNumber() == null) {
                statement.setNull(4, Types.INTEGER);
            } else {
                statement.setInt(4, issue.rowNumber());
            }
            statement.setString(5, issue.field());
            statement.setString(6, json(issue.parameters()));
        }

        @Override
        public int getBatchSize() {
            return issues.size();
        }
    }
}
