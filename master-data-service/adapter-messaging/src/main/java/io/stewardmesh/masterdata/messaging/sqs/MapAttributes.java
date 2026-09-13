package io.stewardmesh.masterdata.messaging.sqs;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import java.util.Map;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;

final class MapAttributes {
    private MapAttributes() {}

    static Map<String, MessageAttributeValue> of(CanonicalEventEnvelope envelope) {
        return Map.of(
                "eventType", value(envelope.eventType()),
                "schemaVersion", value(Integer.toString(envelope.schemaVersion())),
                "correlationId", value(envelope.correlationId().toString()));
    }

    private static MessageAttributeValue value(String value) {
        return MessageAttributeValue.builder().dataType("String").stringValue(value).build();
    }
}
