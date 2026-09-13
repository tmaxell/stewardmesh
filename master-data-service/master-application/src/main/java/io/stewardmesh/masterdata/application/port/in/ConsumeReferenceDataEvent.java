package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.messaging.IngressResult;

@FunctionalInterface
public interface ConsumeReferenceDataEvent extends UseCase<CanonicalEventEnvelope, IngressResult> {}
