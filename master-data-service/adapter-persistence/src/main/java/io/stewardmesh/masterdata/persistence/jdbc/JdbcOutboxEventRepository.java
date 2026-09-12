package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.messaging.OutboxEvent;
import io.stewardmesh.masterdata.application.port.out.OutboxEventRepository;
import java.sql.Timestamp;
import java.sql.Types;
import java.util.Objects;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Inserts immutable business envelopes into the transactional outbox. */
public final class JdbcOutboxEventRepository implements OutboxEventRepository {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcOutboxEventRepository(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void append(OutboxEvent event) {
        Objects.requireNonNull(event, "event must not be null");
        jdbcTemplate.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO outbox_event
                        (event_id, event_type, schema_version, subject_type, subject_id,
                         entity_version, origin_system, producer, occurred_at,
                         correlation_id, causation_id, payload)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """);
            statement.setObject(1, event.eventId());
            statement.setString(2, event.eventType());
            statement.setInt(3, event.schemaVersion());
            statement.setString(4, event.subjectType());
            statement.setObject(5, event.subjectId());
            statement.setLong(6, event.entityVersion());
            statement.setString(7, event.originSystem());
            statement.setString(8, event.producer());
            statement.setTimestamp(9, Timestamp.from(event.occurredAt()));
            statement.setObject(10, event.correlationId());
            statement.setObject(11, event.causationId());
            statement.setObject(
                    12, objectMapper.writeValueAsString(event.payload()), Types.OTHER);
            return statement;
        });
    }
}
