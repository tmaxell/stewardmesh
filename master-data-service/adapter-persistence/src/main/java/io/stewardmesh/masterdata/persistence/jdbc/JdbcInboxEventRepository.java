package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.messaging.InboxRegistration;
import io.stewardmesh.masterdata.application.messaging.IngressOutcome;
import io.stewardmesh.masterdata.application.port.out.InboxEventRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcInboxEventRepository implements InboxEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcInboxEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public InboxRegistration register(
            CanonicalEventEnvelope envelope, String payloadFingerprint, Instant receivedAt) {
        int inserted = jdbcTemplate.update("""
                INSERT INTO inbox_event
                    (event_id, payload_fingerprint, event_type, subject_type, subject_id,
                     entity_version, origin_system, producer, correlation_id, causation_id, received_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id) DO NOTHING
                """, envelope.eventId(), payloadFingerprint, envelope.eventType(), envelope.subjectType(),
                envelope.subjectId(), envelope.entityVersion(), envelope.originSystem(), envelope.producer(),
                envelope.correlationId(), envelope.causationId().orElse(null), Timestamp.from(receivedAt));
        if (inserted == 1) {
            return InboxRegistration.NEW;
        }
        String stored = jdbcTemplate.queryForObject(
                "SELECT payload_fingerprint FROM inbox_event WHERE event_id = ?",
                String.class, envelope.eventId());
        return payloadFingerprint.equals(stored) ? InboxRegistration.DUPLICATE : InboxRegistration.CONFLICT;
    }

    @Override
    public void complete(UUID eventId, IngressOutcome outcome, String reasonCode, Instant completedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE inbox_event
                   SET processing_status = ?, reason_code = ?, completed_at = ?
                 WHERE event_id = ? AND processing_status = 'RECEIVED'
                """, outcome.name(), reasonCode, Timestamp.from(completedAt), eventId);
        if (updated != 1) {
            throw new IllegalStateException("inbox event cannot be completed");
        }
    }
}
