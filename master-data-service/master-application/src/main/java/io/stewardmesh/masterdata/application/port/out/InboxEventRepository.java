package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.messaging.InboxRegistration;
import io.stewardmesh.masterdata.application.messaging.IngressOutcome;
import java.time.Instant;
import java.util.UUID;

public interface InboxEventRepository {
    InboxRegistration register(CanonicalEventEnvelope envelope, String payloadFingerprint, Instant receivedAt);

    void complete(UUID eventId, IngressOutcome outcome, String reasonCode, Instant completedAt);
}
