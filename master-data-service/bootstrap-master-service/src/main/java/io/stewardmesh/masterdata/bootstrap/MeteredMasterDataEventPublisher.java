package io.stewardmesh.masterdata.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.port.out.MasterDataEventPublisher;
import java.util.Locale;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Observes egress at the broker boundary, which is the only place a single failed delivery is
 * visible: the outbox relay releases the claim and moves on, so without this the only symptom of a
 * broker outage would be rows that never reach PUBLISHED.
 */
final class MeteredMasterDataEventPublisher implements MasterDataEventPublisher {

    private static final Logger LOG = LoggerFactory.getLogger(MeteredMasterDataEventPublisher.class);

    private final MasterDataEventPublisher delegate;
    private final MeterRegistry registry;

    MeteredMasterDataEventPublisher(MasterDataEventPublisher delegate, MeterRegistry registry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public String transportSystem() {
        return delegate.transportSystem();
    }

    @Override
    public String publish(CanonicalEventEnvelope envelope) {
        try {
            String messageId = delegate.publish(envelope);
            count(envelope, "published");
            return messageId;
        } catch (RuntimeException failure) {
            count(envelope, "failed");
            LOG.error(
                    "mastered event delivery failed transport={} eventId={} eventType={}",
                    delegate.transportSystem(),
                    envelope.eventId(),
                    envelope.eventType());
            throw failure;
        }
    }

    private void count(CanonicalEventEnvelope envelope, String result) {
        registry.counter(
                        "stewardmesh.messaging.publications",
                        "transport",
                        tag(delegate.transportSystem()),
                        "event_type",
                        tag(envelope.eventType()),
                        "result",
                        result)
                .increment();
    }

    private static String tag(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
