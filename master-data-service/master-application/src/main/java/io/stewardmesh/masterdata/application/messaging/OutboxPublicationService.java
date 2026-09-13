package io.stewardmesh.masterdata.application.messaging;

import io.stewardmesh.masterdata.application.port.in.PublishMasterDataEvents;
import io.stewardmesh.masterdata.application.port.out.MasterDataEventPublisher;
import io.stewardmesh.masterdata.application.port.out.OutboxPublicationRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/** Claims and relays immutable mastered events with safe at-least-once retry semantics. */
public final class OutboxPublicationService implements PublishMasterDataEvents {
    private final OutboxPublicationRepository outbox;
    private final MasterDataEventPublisher publisher;
    private final Clock clock;
    private final Duration claimTimeout;

    public OutboxPublicationService(
            OutboxPublicationRepository outbox,
            MasterDataEventPublisher publisher,
            Clock clock,
            Duration claimTimeout) {
        this.outbox = Objects.requireNonNull(outbox, "outbox must not be null");
        this.publisher = Objects.requireNonNull(publisher, "publisher must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        this.claimTimeout = Objects.requireNonNull(claimTimeout, "claimTimeout must not be null");
        if (claimTimeout.isZero() || claimTimeout.isNegative()) {
            throw new IllegalArgumentException("claimTimeout must be positive");
        }
    }

    @Override
    public Integer execute(Integer batchSize) {
        if (batchSize < 1 || batchSize > 100) {
            throw new IllegalArgumentException("batchSize must be between 1 and 100");
        }
        var now = clock.instant();
        var publications = outbox.claim(batchSize, now, now.minus(claimTimeout));
        int published = 0;
        for (var publication : publications) {
            var event = publication.event();
            try {
                String messageId = publisher.publish(new CanonicalEventEnvelope(
                        event.eventId(), event.eventType(), event.schemaVersion(), event.subjectType(),
                        event.subjectId().toString(), event.entityVersion(), event.originSystem(), event.producer(),
                        "sqs", event.occurredAt(), now, event.correlationId(), Optional.of(event.causationId()),
                        event.correlationId().toString(), CanonicalEventEnvelope.DataClassification.INTERNAL,
                        event.payload()));
                outbox.markPublished(event.eventId(), messageId, clock.instant());
                published++;
            } catch (RuntimeException failure) {
                outbox.release(event.eventId());
            }
        }
        return published;
    }
}
