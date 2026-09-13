package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.audit.AuditEvent;
import io.stewardmesh.masterdata.application.port.out.AuditEventRepository;
import java.sql.Array;
import java.sql.Timestamp;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;

/** Append-only PostgreSQL audit adapter. */
public final class JdbcAuditEventRepository implements AuditEventRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public void append(AuditEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO audit_event
                        (audit_id, plan_id, plan_version, plan_hash, actor_subject,
                         action, result, reason, occurred_at, correlation_id, affected_entities)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            statement.setObject(1, event.auditId());
            statement.setObject(2, event.planId().value());
            statement.setLong(3, event.planVersion().value());
            statement.setString(4, event.planHash().value());
            statement.setString(5, event.actorSubject());
            statement.setString(6, event.action());
            statement.setString(7, event.result());
            statement.setString(8, event.reason());
            statement.setTimestamp(9, Timestamp.from(event.occurredAt()));
            statement.setObject(10, event.correlationId());
            Array affected = connection.createArrayOf("uuid", event.affectedEntities().toArray());
            statement.setArray(11, affected);
            return statement;
        });
    }
}
