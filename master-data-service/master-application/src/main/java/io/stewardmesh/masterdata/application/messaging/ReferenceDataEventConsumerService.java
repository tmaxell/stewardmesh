package io.stewardmesh.masterdata.application.messaging;

import io.stewardmesh.masterdata.application.port.in.ConsumeReferenceDataEvent;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.InboxEventRepository;
import io.stewardmesh.masterdata.application.port.out.QuarantineEventRepository;
import io.stewardmesh.masterdata.application.port.out.ReferenceDataEventApplier;
import java.time.Clock;
import java.time.Instant;
import java.util.Objects;

/** Transactional inbox boundary for at-least-once reference-event delivery. */
public final class ReferenceDataEventConsumerService implements ConsumeReferenceDataEvent {
    private final InboxEventRepository inbox;
    private final QuarantineEventRepository quarantine;
    private final ReferenceDataEventApplier applier;
    private final ApplicationTransaction transaction;
    private final Clock clock;

    public ReferenceDataEventConsumerService(
            InboxEventRepository inbox,
            QuarantineEventRepository quarantine,
            ReferenceDataEventApplier applier,
            ApplicationTransaction transaction,
            Clock clock) {
        this.inbox = Objects.requireNonNull(inbox, "inbox must not be null");
        this.quarantine = Objects.requireNonNull(quarantine, "quarantine must not be null");
        this.applier = Objects.requireNonNull(applier, "applier must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public IngressResult execute(CanonicalEventEnvelope envelope) {
        Objects.requireNonNull(envelope, "envelope must not be null");
        return transaction.execute(() -> consume(envelope, clock.instant()));
    }

    private IngressResult consume(CanonicalEventEnvelope envelope, Instant now) {
        String fingerprint = CanonicalEventFingerprint.of(envelope);
        InboxRegistration registration = inbox.register(envelope, fingerprint, now);
        if (registration == InboxRegistration.DUPLICATE) {
            return new IngressResult(envelope.eventId(), IngressOutcome.DUPLICATE, "DUPLICATE_EVENT");
        }
        if (registration == InboxRegistration.CONFLICT) {
            quarantine.append(envelope, fingerprint, "EVENT_ID_CONFLICT", now);
            return new IngressResult(envelope.eventId(), IngressOutcome.REJECTED, "EVENT_ID_CONFLICT");
        }
        if ("STEWARDMESH".equals(envelope.originSystem())) {
            inbox.complete(envelope.eventId(), IngressOutcome.LOOP_SUPPRESSED, "OWN_EVENT_RELAY", now);
            return new IngressResult(envelope.eventId(), IngressOutcome.LOOP_SUPPRESSED, "OWN_EVENT_RELAY");
        }
        try {
            applier.apply(envelope);
            inbox.complete(envelope.eventId(), IngressOutcome.ACCEPTED, "APPLIED", now);
            return new IngressResult(envelope.eventId(), IngressOutcome.ACCEPTED, "APPLIED");
        } catch (ReferenceEventRejectedException rejected) {
            quarantine.append(envelope, fingerprint, rejected.reasonCode(), now);
            inbox.complete(envelope.eventId(), IngressOutcome.REJECTED, rejected.reasonCode(), now);
            return new IngressResult(envelope.eventId(), IngressOutcome.REJECTED, rejected.reasonCode());
        }
    }
}
