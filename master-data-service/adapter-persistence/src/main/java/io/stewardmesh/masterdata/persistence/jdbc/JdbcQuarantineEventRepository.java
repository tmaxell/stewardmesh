package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.port.out.QuarantineEventRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcQuarantineEventRepository implements QuarantineEventRepository {
    private final JdbcTemplate jdbcTemplate;

    public JdbcQuarantineEventRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public void append(
            CanonicalEventEnvelope envelope,
            String payloadFingerprint,
            String reasonCode,
            Instant quarantinedAt) {
        jdbcTemplate.update("""
                INSERT INTO quarantine_event
                    (quarantine_id, event_id, payload_fingerprint, reason_code,
                     origin_system, subject_type, subject_id, quarantined_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), envelope.eventId(), payloadFingerprint, reasonCode,
                envelope.originSystem(), envelope.subjectType(), envelope.subjectId(),
                Timestamp.from(quarantinedAt));
    }
}
