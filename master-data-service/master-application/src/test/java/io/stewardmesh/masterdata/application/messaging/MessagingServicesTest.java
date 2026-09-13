package io.stewardmesh.masterdata.application.messaging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class MessagingServicesTest {
    private static final Instant NOW = Instant.parse("2026-09-13T08:00:00Z");

    @Test
    void appliesOnceAndSuppressesDuplicatesAndOwnRelays() {
        var inbox = new MemoryInbox();
        var applied = new ArrayList<CanonicalEventEnvelope>();
        var quarantined = new ArrayList<String>();
        var service = consumer(inbox, applied, quarantined);
        var event = event(UUID.fromString("00000000-0000-0000-0000-000000000801"), "synthetic-nsi");

        assertEquals(IngressOutcome.ACCEPTED, service.execute(event).outcome());
        assertEquals(IngressOutcome.DUPLICATE, service.execute(event).outcome());
        assertEquals(IngressOutcome.LOOP_SUPPRESSED, service.execute(event(
                UUID.fromString("00000000-0000-0000-0000-000000000802"), "STEWARDMESH")).outcome());
        assertEquals(1, applied.size());
        assertEquals(0, quarantined.size());
    }

    @Test
    void quarantinesConflictingIdentityAndPermanentRejection() {
        var inbox = new MemoryInbox();
        var quarantined = new ArrayList<String>();
        var service = consumer(inbox, new ArrayList<>(), quarantined);
        var original = event(UUID.fromString("00000000-0000-0000-0000-000000000803"), "synthetic-nsi");
        service.execute(original);
        var conflict = copy(original, Map.of("code", "SYNTHETIC-OTHER"));

        assertEquals(IngressOutcome.REJECTED, service.execute(conflict).outcome());

        var rejecting = new ReferenceDataEventConsumerService(
                inbox, (ignored, hash, reason, at) -> quarantined.add(reason),
                ignored -> { throw new ReferenceEventRejectedException("STALE_VERSION", "stale"); },
                new DirectTransaction(), Clock.fixed(NOW, ZoneOffset.UTC));
        assertEquals(IngressOutcome.REJECTED, rejecting.execute(event(
                UUID.fromString("00000000-0000-0000-0000-000000000804"), "synthetic-nsi")).outcome());
        assertEquals(List.of("EVENT_ID_CONFLICT", "STALE_VERSION"), quarantined);
    }

    @Test
    void publishesClaimedEventsAndReleasesTransientFailures() {
        var repository = new MemoryOutbox();
        repository.claimed.add(new OutboxPublication(outboxEvent(1), 1));
        repository.claimed.add(new OutboxPublication(outboxEvent(2), 1));
        var publisher = new StubPublisher();
        var service = new OutboxPublicationService(
                repository,
                publisher,
                Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofMinutes(5));

        assertEquals(1, service.execute(10));
        assertEquals(List.of(outboxEvent(1).eventId()), repository.published);
        assertEquals(List.of(outboxEvent(2).eventId()), repository.released);
        assertEquals("stub-broker", publisher.delivered.getFirst().transportSystem());
        assertThrows(IllegalArgumentException.class, () -> service.execute(0));
    }

    @Test
    void mapsBusinessUnitReferenceAndRejectsVersionGaps() {
        var saved = new AtomicReference<io.stewardmesh.masterdata.domain.organization.BusinessUnit>();
        var applier = new BusinessUnitReferenceEventApplier(
                ignored -> { throw new io.stewardmesh.masterdata.application.organization.BusinessUnitNotFoundException(); },
                unit -> { saved.set(unit); return unit; });
        var envelope = copy(event(UUID.fromString("00000000-0000-0000-0000-000000000805"), "synthetic-nsi"),
                Map.of("code", "SYNTHETIC_805", "displayName", "Synthetic Unit 805",
                        "roles", "CLIENT,PROCUREMENT", "validFrom", "2026-09-13"));

        applier.apply(new CanonicalEventEnvelope(
                envelope.eventId(), BusinessUnitReferenceEventApplier.EVENT_TYPE, 1, "BUSINESS_UNIT",
                "00000000-0000-0000-0000-000000000805", 1, envelope.originSystem(), envelope.producer(),
                envelope.transportSystem(), envelope.occurredAt(), envelope.publishedAt(), envelope.correlationId(),
                envelope.causationId(), envelope.traceId(), envelope.dataClassification(), envelope.payload()));
        assertEquals("SYNTHETIC_805", saved.get().code().value());

        var gap = new CanonicalEventEnvelope(
                UUID.fromString("00000000-0000-0000-0000-000000000806"),
                BusinessUnitReferenceEventApplier.EVENT_TYPE, 1, "BUSINESS_UNIT",
                "00000000-0000-0000-0000-000000000806", 2, envelope.originSystem(), envelope.producer(),
                envelope.transportSystem(), envelope.occurredAt(), envelope.publishedAt(), envelope.correlationId(),
                envelope.causationId(), envelope.traceId(), envelope.dataClassification(), envelope.payload());
        assertEquals("VERSION_GAP", assertThrows(ReferenceEventRejectedException.class,
                () -> applier.apply(gap)).reasonCode());
    }

    private static ReferenceDataEventConsumerService consumer(
            MemoryInbox inbox, List<CanonicalEventEnvelope> applied, List<String> quarantined) {
        return new ReferenceDataEventConsumerService(
                inbox, (ignored, hash, reason, at) -> quarantined.add(reason), applied::add,
                new DirectTransaction(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CanonicalEventEnvelope event(UUID id, String origin) {
        return new CanonicalEventEnvelope(
                id, "BusinessUnitReferenceChanged", 1, "BUSINESS_UNIT", "synthetic-client-801", 1,
                origin, "synthetic-distributor", "sqs", NOW.minusSeconds(2), NOW.minusSeconds(1),
                UUID.fromString("00000000-0000-0000-0000-000000000899"), Optional.empty(),
                "synthetic-trace-801", CanonicalEventEnvelope.DataClassification.INTERNAL,
                Map.of("code", "SYNTHETIC-801"));
    }

    private static CanonicalEventEnvelope copy(CanonicalEventEnvelope source, Map<String, String> payload) {
        return new CanonicalEventEnvelope(
                source.eventId(), source.eventType(), source.schemaVersion(), source.subjectType(), source.subjectId(),
                source.entityVersion(), source.originSystem(), source.producer(), source.transportSystem(),
                source.occurredAt(), source.publishedAt(), source.correlationId(), source.causationId(),
                source.traceId(), source.dataClassification(), payload);
    }

    private static OutboxEvent outboxEvent(long version) {
        return new OutboxEvent(
                new UUID(0, version), "SupplierGoldenRecordChanged", 1, "SUPPLIER_PARTY",
                new UUID(1, version), version, "STEWARDMESH", "master-service", NOW,
                new UUID(2, version), new UUID(3, version), Map.of("effect", "SYNTHETIC"));
    }

    private static final class MemoryInbox implements io.stewardmesh.masterdata.application.port.out.InboxEventRepository {
        private final Map<UUID, String> fingerprints = new HashMap<>();

        @Override
        public InboxRegistration register(CanonicalEventEnvelope envelope, String fingerprint, Instant receivedAt) {
            String previous = fingerprints.putIfAbsent(envelope.eventId(), fingerprint);
            if (previous == null) return InboxRegistration.NEW;
            return previous.equals(fingerprint) ? InboxRegistration.DUPLICATE : InboxRegistration.CONFLICT;
        }

        @Override
        public void complete(UUID eventId, IngressOutcome outcome, String reasonCode, Instant completedAt) {}
    }

    private static final class MemoryOutbox implements io.stewardmesh.masterdata.application.port.out.OutboxPublicationRepository {
        private final List<OutboxPublication> claimed = new ArrayList<>();
        private final List<UUID> published = new ArrayList<>();
        private final List<UUID> released = new ArrayList<>();

        @Override public List<OutboxPublication> claim(int size, Instant at, Instant reclaimBefore) { return List.copyOf(claimed); }
        @Override public void markPublished(UUID id, String messageId, Instant at) { published.add(id); }
        @Override public void release(UUID id) { released.add(id); }
    }

    private static final class DirectTransaction implements ApplicationTransaction {
        @Override public <T> T execute(java.util.function.Supplier<T> work) { return work.get(); }
    }

    /** Stands in for a broker adapter, including the transport identity it stamps on the envelope. */
    private static final class StubPublisher
            implements io.stewardmesh.masterdata.application.port.out.MasterDataEventPublisher {

        @Override
        public String transportSystem() {
            return "stub-broker";
        }

        private final List<CanonicalEventEnvelope> delivered = new java.util.ArrayList<>();

        @Override
        public String publish(CanonicalEventEnvelope envelope) {
            if (envelope.entityVersion() == 2) {
                throw new IllegalStateException("synthetic outage");
            }
            delivered.add(envelope);
            return "message-801";
        }
    }

}
