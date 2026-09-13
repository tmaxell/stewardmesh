package io.stewardmesh.masterdata.messaging.sqs;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import java.util.Objects;
import tools.jackson.databind.ObjectMapper;

public final class SqsCanonicalEventCodec {
    private final ObjectMapper objectMapper;

    public SqsCanonicalEventCodec(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    public String encode(CanonicalEventEnvelope envelope) {
        return objectMapper.writeValueAsString(Objects.requireNonNull(envelope, "envelope must not be null"));
    }

    public CanonicalEventEnvelope decode(String body) {
        Objects.requireNonNull(body, "body must not be null");
        return objectMapper.readValue(body, CanonicalEventEnvelope.class);
    }
}
