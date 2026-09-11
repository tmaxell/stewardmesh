package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.identity.StewardshipCaseWriteException;
import io.stewardmesh.masterdata.application.port.out.StoreStewardshipCase;
import io.stewardmesh.masterdata.domain.stewardship.StewardshipCase;
import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcStewardshipCaseStore implements StoreStewardshipCase {

    private final JdbcTemplate jdbcTemplate;

    public JdbcStewardshipCaseStore(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public void save(StewardshipCase stewardshipCase) {
        Objects.requireNonNull(stewardshipCase, "stewardshipCase must not be null");
        var identity = stewardshipCase.sourceRecordIdentity();
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO stewardship_case
                        (origin_system, source_record_id, source_version, ruleset_id,
                         reason, status, opened_at)
                    VALUES (?, ?, ?, ?, ?, 'OPEN', ?)
                    ON CONFLICT DO NOTHING
                    """,
                    identity.originSystem().value(),
                    identity.sourceRecordId(),
                    identity.sourceVersion(),
                    stewardshipCase.rulesetId().value(),
                    stewardshipCase.reason().name(),
                    Timestamp.from(stewardshipCase.openedAt()));
        } catch (DataAccessException exception) {
            throw new StewardshipCaseWriteException(
                    "stewardship case could not be persisted", exception);
        }
    }
}
