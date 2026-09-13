package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;

/**
 * Broker-neutral egress. The adapter owns its transport identity so a second broker can be added
 * without touching the domain or application layers.
 */
public interface MasterDataEventPublisher {

    /** Stable short name of the transport this adapter delivers through, for example {@code sqs}. */
    String transportSystem();

    String publish(CanonicalEventEnvelope envelope);
}
