package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.messaging.OutboxEvent;
import io.stewardmesh.masterdata.application.messaging.OutboxPublication;
import io.stewardmesh.masterdata.application.port.out.OutboxEventRepository;
import io.stewardmesh.masterdata.application.port.out.OutboxPublicationRepository;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.ObjectMapper;

/** Inserts immutable business envelopes into the transactional outbox. */
public final class JdbcOutboxEventRepository implements OutboxEventRepository, OutboxPublicationRepository {

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

    @Override
    public List<OutboxPublication> claim(int batchSize, Instant claimedAt, Instant reclaimBefore) {
        return jdbcTemplate.query(connection -> {
            var statement = connection.prepareStatement("""
                    WITH candidates AS (
                        SELECT event_id FROM outbox_event
                         WHERE publication_status = 'PENDING'
                            OR (publication_status = 'CLAIMED' AND claimed_at < ?)
                         ORDER BY occurred_at, event_id
                         FOR UPDATE SKIP LOCKED
                         LIMIT ?
                    )
                    UPDATE outbox_event event
                       SET publication_status = 'CLAIMED', claimed_at = ?,
                           publish_attempts = publish_attempts + 1
                      FROM candidates
                     WHERE event.event_id = candidates.event_id
                    RETURNING event.*
                    """);
            statement.setTimestamp(1, Timestamp.from(reclaimBefore));
            statement.setInt(2, batchSize);
            statement.setTimestamp(3, Timestamp.from(claimedAt));
            return statement;
        }, (result, row) -> new OutboxPublication(new OutboxEvent(
                result.getObject("event_id", UUID.class), result.getString("event_type"),
                result.getInt("schema_version"), result.getString("subject_type"),
                result.getObject("subject_id", UUID.class), result.getLong("entity_version"),
                result.getString("origin_system"), result.getString("producer"),
                result.getTimestamp("occurred_at").toInstant(),
                result.getObject("correlation_id", UUID.class),
                result.getObject("causation_id", UUID.class), readPayload(result.getString("payload"))),
                result.getInt("publish_attempts")));
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> readPayload(String json) {
        return objectMapper.readValue(json, Map.class);
    }

    @Override
    public void markPublished(UUID eventId, String brokerMessageId, Instant publishedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE outbox_event SET publication_status = 'PUBLISHED', published_at = ?,
                       broker_message_id = ?, claimed_at = NULL
                 WHERE event_id = ? AND publication_status = 'CLAIMED'
                """, Timestamp.from(publishedAt), brokerMessageId, eventId);
        requireUpdate(updated, "published");
    }

    @Override
    public void release(UUID eventId) {
        int updated = jdbcTemplate.update("""
                UPDATE outbox_event SET publication_status = 'PENDING', claimed_at = NULL
                 WHERE event_id = ? AND publication_status = 'CLAIMED'
                """, eventId);
        requireUpdate(updated, "released");
    }

    private static void requireUpdate(int updated, String transition) {
        if (updated != 1) {
            throw new IllegalStateException("outbox event cannot be " + transition);
        }
    }
}
