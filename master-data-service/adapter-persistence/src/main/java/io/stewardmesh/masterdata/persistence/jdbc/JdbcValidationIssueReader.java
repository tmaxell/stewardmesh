package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.intake.SupplierImportReport;
import io.stewardmesh.masterdata.application.intake.SupplierImportReportQuery;
import io.stewardmesh.masterdata.application.port.out.ValidationIssueReader;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

public class JdbcValidationIssueReader implements ValidationIssueReader {

    private static final TypeReference<Map<String, String>> PARAMETERS_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcValidationIssueReader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    @Transactional(readOnly = true)
    public SupplierImportReport read(SupplierImportReportQuery query) {
        Long totalIssues = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM validation_issue WHERE import_job_id = ?",
                Long.class,
                query.importJobId().value());
        List<ValidationIssue> issues = jdbcTemplate.query(
                """
                SELECT code, row_number, field_name, parameters::text
                FROM validation_issue
                WHERE import_job_id = ?
                ORDER BY issue_index
                LIMIT ? OFFSET ?
                """,
                (resultSet, rowNumber) -> new ValidationIssue(
                        ValidationCode.valueOf(resultSet.getString("code")),
                        resultSet.getObject("row_number", Integer.class),
                        resultSet.getString("field_name"),
                        readParameters(resultSet.getString("parameters"))),
                query.importJobId().value(),
                query.size(),
                Math.multiplyExact(query.page(), query.size()));
        return new SupplierImportReport(
                query.importJobId(), query.page(), query.size(), totalIssues.longValue(), issues);
    }

    private Map<String, String> readParameters(String json) {
        try {
            return objectMapper.readValue(json, PARAMETERS_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored validation parameters are invalid", exception);
        }
    }
}
