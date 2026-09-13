package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import java.time.Instant;

@FunctionalInterface
public interface QuarantineEventRepository {
    void append(CanonicalEventEnvelope envelope, String payloadFingerprint, String reasonCode, Instant quarantinedAt);
}
