package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;

@FunctionalInterface
public interface ReferenceDataEventApplier {
    void apply(CanonicalEventEnvelope envelope);
}
