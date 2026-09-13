package io.stewardmesh.masterdata.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.messaging.IngressOutcome;
import io.stewardmesh.masterdata.application.messaging.IngressResult;
import io.stewardmesh.masterdata.application.port.in.ConsumeReferenceDataEvent;
import java.time.Clock;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Makes inbox ingress observable. Outcome and reason are closed vocabularies, so both are safe
 * labels; event identifiers and payloads never become labels or log content beyond the event id and
 * its declared type.
 */
final class MeteredConsumeReferenceDataEvent implements ConsumeReferenceDataEvent {

    private static final Logger LOG = LoggerFactory.getLogger(MeteredConsumeReferenceDataEvent.class);

    private final ConsumeReferenceDataEvent delegate;
    private final MeterRegistry registry;
    private final Clock clock;

    MeteredConsumeReferenceDataEvent(
            ConsumeReferenceDataEvent delegate, MeterRegistry registry, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public IngressResult execute(CanonicalEventEnvelope envelope) {
        try {
            IngressResult result = delegate.execute(envelope);
            registry.counter(
                            "stewardmesh.messaging.ingress",
                            "outcome",
                            tag(result.outcome().name()),
                            "reason",
                            tag(result.reasonCode()))
                    .increment();
            recordLag(envelope);
            report(envelope, result);
            return result;
        } catch (RuntimeException failure) {
            registry.counter(
                            "stewardmesh.messaging.ingress.failures",
                            "reason",
                            tag(failure.getClass().getSimpleName()))
                    .increment();
            LOG.error(
                    "reference event ingress failed eventId={} eventType={}",
                    envelope.eventId(),
                    envelope.eventType());
            throw failure;
        }
    }

    private void recordLag(CanonicalEventEnvelope envelope) {
        Duration lag = Duration.between(envelope.occurredAt(), clock.instant());
        if (!lag.isNegative()) {
            registry.timer("stewardmesh.messaging.ingress.lag", "event_type", tag(envelope.eventType()))
                    .record(lag.toMillis(), TimeUnit.MILLISECONDS);
        }
    }

    private static void report(CanonicalEventEnvelope envelope, IngressResult result) {
        if (result.outcome() == IngressOutcome.REJECTED) {
            LOG.warn(
                    "reference event quarantined eventId={} eventType={} reason={}",
                    envelope.eventId(),
                    envelope.eventType(),
                    result.reasonCode());
        } else if (result.outcome() == IngressOutcome.LOOP_SUPPRESSED) {
            LOG.info(
                    "own event suppressed eventId={} eventType={} reason={}",
                    envelope.eventId(),
                    envelope.eventType(),
                    result.reasonCode());
        }
    }

    private static String tag(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
