package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.messaging.OutboxPublication;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface OutboxPublicationRepository {
    List<OutboxPublication> claim(int batchSize, Instant claimedAt, Instant reclaimBefore);

    void markPublished(UUID eventId, String brokerMessageId, Instant publishedAt);

    void release(UUID eventId);
}
