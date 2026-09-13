package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.messaging.InboxRegistration;
import io.stewardmesh.masterdata.application.messaging.IngressOutcome;
import io.stewardmesh.masterdata.application.messaging.OutboxEvent;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcInboxEventRepository;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcOutboxEventRepository;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcQuarantineEventRepository;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.UncategorizedSQLException;
import tools.jackson.databind.ObjectMapper;

class MessagingPersistenceIT extends PostgreSqlIntegrationTestSupport {
    private static final Instant NOW = Instant.parse("2026-09-13T09:00:00Z");

    @Test
    void transactionallyDeduplicatesAndProtectsInboxContent() {
        var repository = new JdbcInboxEventRepository(jdbcTemplate());
        var event = inbound(new UUID(0, 901), Map.of("code", "SYNTHETIC-901"));
        String fingerprint = "a".repeat(64);

        assertEquals(InboxRegistration.NEW, repository.register(event, fingerprint, NOW));
        repository.complete(event.eventId(), IngressOutcome.ACCEPTED, "APPLIED", NOW.plusSeconds(1));
        assertEquals(InboxRegistration.DUPLICATE, repository.register(event, fingerprint, NOW.plusSeconds(2)));
        assertEquals(InboxRegistration.CONFLICT, repository.register(event, "b".repeat(64), NOW.plusSeconds(2)));
        assertThrows(UncategorizedSQLException.class, () -> jdbcTemplate().update(
                "UPDATE inbox_event SET subject_id = 'changed' WHERE event_id = ?", event.eventId()));
    }

    @Test
    void recordsRedactedQuarantineEvidence() {
        var repository = new JdbcQuarantineEventRepository(jdbcTemplate());
        var event = inbound(new UUID(0, 902), Map.of("displayName", "Synthetic Unit"));

        repository.append(event, "c".repeat(64), "STALE_VERSION", NOW);

        assertEquals(1, jdbcTemplate().queryForObject(
                "SELECT count(*) FROM quarantine_event WHERE event_id = ? AND reason_code = 'STALE_VERSION'",
                Integer.class, event.eventId()));
    }

    @Test
    void claimsReleasesReclaimsAndPublishesOutboxRows() {
        var repository = new JdbcOutboxEventRepository(jdbcTemplate(), new ObjectMapper());
        var event = outbound(new UUID(0, 903));
        repository.append(event);

        var first = repository.claim(10, NOW, NOW.minusSeconds(300));
        assertEquals(1, first.stream().filter(item -> item.event().eventId().equals(event.eventId())).count());
        repository.release(event.eventId());
        var second = repository.claim(10, NOW.plusSeconds(1), NOW.minusSeconds(300));
        assertEquals(2, second.stream()
                .filter(item -> item.event().eventId().equals(event.eventId())).findFirst().orElseThrow().attempt());
        repository.markPublished(event.eventId(), "synthetic-message-903", NOW.plusSeconds(2));
        assertEquals("PUBLISHED", jdbcTemplate().queryForObject(
                "SELECT publication_status FROM outbox_event WHERE event_id = ?", String.class, event.eventId()));
    }

    private static CanonicalEventEnvelope inbound(UUID id, Map<String, String> payload) {
        return new CanonicalEventEnvelope(
                id, "BusinessUnitReferenceChanged", 1, "BUSINESS_UNIT", "synthetic-client-901", 1,
                "SYNTHETIC_ERP", "synthetic-distributor", "sqs", NOW.minusSeconds(2), NOW.minusSeconds(1),
                new UUID(1, 901), Optional.empty(), "synthetic-trace-901",
                CanonicalEventEnvelope.DataClassification.INTERNAL, payload);
    }

    private static OutboxEvent outbound(UUID id) {
        return new OutboxEvent(
                id, "SupplierGoldenRecordChanged", 1, "SUPPLIER_PARTY", new UUID(1, 903), 1,
                "STEWARDMESH", "master-service", NOW, new UUID(2, 903), new UUID(3, 903),
                Map.of("effect", "SYNTHETIC"));
    }
}
