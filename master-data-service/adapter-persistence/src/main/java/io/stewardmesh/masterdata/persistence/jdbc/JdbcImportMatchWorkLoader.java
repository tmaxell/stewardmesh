package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.identity.ImportMatchWorkItem;
import io.stewardmesh.masterdata.application.port.out.LoadImportMatchWork;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcImportMatchWorkLoader implements LoadImportMatchWork {

    private final JdbcTemplate jdbcTemplate;

    public JdbcImportMatchWorkLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public List<ImportMatchWorkItem> load(ImportJobId importJobId) {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        return jdbcTemplate.query(
                """
                SELECT sr.origin_system, sr.source_record_id, sr.source_version,
                       sr.source_version = (
                           SELECT MAX(latest.source_version)
                           FROM source_record latest
                           WHERE latest.origin_system = sr.origin_system
                             AND latest.source_record_id = sr.source_record_id
                       ) AS latest_version
                FROM source_record sr
                WHERE sr.import_job_id = ?
                ORDER BY sr.origin_system, sr.source_record_id, sr.source_version
                """,
                (resultSet, rowNumber) -> new ImportMatchWorkItem(
                        new SourceRecordIdentity(
                                new SourceSystemRef(resultSet.getString("origin_system")),
                                resultSet.getString("source_record_id"),
                                resultSet.getLong("source_version")),
                        resultSet.getBoolean("latest_version")),
                importJobId.value());
    }
}
