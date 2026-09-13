package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope.DataClassification;
import io.stewardmesh.masterdata.application.messaging.IngressOutcome;
import io.stewardmesh.masterdata.application.messaging.IngressResult;
import io.stewardmesh.masterdata.application.port.in.ConsumeReferenceDataEvent;
import io.stewardmesh.masterdata.application.port.out.MasterDataEventPublisher;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeteredMessagingTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:05Z");
    private static final UUID EVENT_ID = UUID.fromString("00000000-0000-0000-0000-0000000009a1");

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();

    @Test
    void countsIngressByOutcomeAndReasonAndRecordsLag() {
        ConsumeReferenceDataEvent delegate = envelope ->
                new IngressResult(envelope.eventId(), IngressOutcome.ACCEPTED, "APPLIED");
        var metered = new MeteredConsumeReferenceDataEvent(
                delegate, registry, Clock.fixed(NOW, ZoneOffset.UTC));

        metered.execute(envelope("BusinessUnitReferenceChanged", "synthetic-distributor"));

        assertEquals(
                1,
                registry.counter(
                                "stewardmesh.messaging.ingress",
                                Tags.of("outcome", "accepted", "reason", "applied"))
                        .count());
        assertEquals(
                1,
                registry.timer(
                                "stewardmesh.messaging.ingress.lag",
                                Tags.of("event_type", "businessunitreferencechanged"))
                        .count());
    }

    @Test
    void separatesQuarantineAndLoopSuppressionFromAcceptedEvents() {
        var metered = new MeteredConsumeReferenceDataEvent(
                envelope -> new IngressResult(
                        envelope.eventId(), IngressOutcome.LOOP_SUPPRESSED, "OWN_EVENT_RELAY"),
                registry,
                Clock.fixed(NOW, ZoneOffset.UTC));
        var quarantining = new MeteredConsumeReferenceDataEvent(
                envelope -> new IngressResult(
                        envelope.eventId(), IngressOutcome.REJECTED, "STALE_VERSION"),
                registry,
                Clock.fixed(NOW, ZoneOffset.UTC));

        metered.execute(envelope("SupplierCreated", "STEWARDMESH"));
        quarantining.execute(envelope("BusinessUnitReferenceChanged", "synthetic-distributor"));

        assertEquals(
                1,
                registry.counter(
                                "stewardmesh.messaging.ingress",
                                Tags.of("outcome", "loop_suppressed", "reason", "own_event_relay"))
                        .count());
        assertEquals(
                1,
                registry.counter(
                                "stewardmesh.messaging.ingress",
                                Tags.of("outcome", "rejected", "reason", "stale_version"))
                        .count());
    }

    @Test
    void countsDeliveryOutcomesAtTheBrokerBoundary() {
        var publisher = new MeteredMasterDataEventPublisher(new StubPublisher(false), registry);
        var failing = new MeteredMasterDataEventPublisher(new StubPublisher(true), registry);
        var event = envelope("SupplierCreated", "STEWARDMESH");

        assertEquals("message-1", publisher.publish(event));
        assertEquals("stub", publisher.transportSystem());
        assertThrows(IllegalStateException.class, () -> failing.publish(event));

        assertEquals(
                1,
                registry.counter(
                                "stewardmesh.messaging.publications",
                                Tags.of(
                                        "transport", "stub",
                                        "event_type", "suppliercreated",
                                        "result", "published"))
                        .count());
        assertEquals(
                1,
                registry.counter(
                                "stewardmesh.messaging.publications",
                                Tags.of(
                                        "transport", "stub",
                                        "event_type", "suppliercreated",
                                        "result", "failed"))
                        .count());
    }

    @Test
    void neverLabelsAMetricWithAnEventIdentifierOrProducer() {
        var metered = new MeteredConsumeReferenceDataEvent(
                envelope -> new IngressResult(envelope.eventId(), IngressOutcome.ACCEPTED, "APPLIED"),
                registry,
                Clock.fixed(NOW, ZoneOffset.UTC));

        metered.execute(envelope("BusinessUnitReferenceChanged", "synthetic-distributor"));
        new MeteredMasterDataEventPublisher(new StubPublisher(false), registry)
                .publish(envelope("SupplierCreated", "STEWARDMESH"));

        var labels = registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .map(tag -> tag.getValue())
                .toList();
        assertTrue(labels.stream().noneMatch(value -> value.contains(EVENT_ID.toString())));
        assertTrue(labels.stream().noneMatch(value -> value.contains("synthetic-distributor")));
        assertEquals(
                Map.of(
                        "outcome", 1L,
                        "reason", 1L,
                        "event_type", 2L,
                        "transport", 1L,
                        "result", 1L),
                registry.getMeters().stream()
                        .flatMap(meter -> meter.getId().getTags().stream())
                        .collect(java.util.stream.Collectors.groupingBy(
                                tag -> tag.getKey(), java.util.stream.Collectors.counting())));
    }

    private static CanonicalEventEnvelope envelope(String eventType, String originSystem) {
        return new CanonicalEventEnvelope(
                EVENT_ID,
                eventType,
                1,
                "BUSINESS_UNIT",
                UUID.fromString("00000000-0000-0000-0000-0000000009a2").toString(),
                1,
                originSystem,
                "synthetic-distributor",
                "stub",
                NOW.minusSeconds(5),
                NOW.minusSeconds(1),
                UUID.fromString("00000000-0000-0000-0000-0000000009a3"),
                Optional.of(UUID.fromString("00000000-0000-0000-0000-0000000009a4")),
                UUID.fromString("00000000-0000-0000-0000-0000000009a3").toString(),
                DataClassification.INTERNAL,
                Map.of("code", "SYNTHETIC-BU"));
    }

    private record StubPublisher(boolean failing) implements MasterDataEventPublisher {

        @Override
        public String transportSystem() {
            return "stub";
        }

        @Override
        public String publish(CanonicalEventEnvelope envelope) {
            if (failing) {
                throw new IllegalStateException("synthetic broker outage");
            }
            return "message-1";
        }
    }
}
