package io.stewardmesh.masterdata.messaging.sqs;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.port.out.MasterDataEventPublisher;
import java.util.Objects;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

public final class SqsMasterDataEventPublisher implements MasterDataEventPublisher {
    private final SqsClient sqs;
    private final String queue;
    private final SqsCanonicalEventCodec codec;

    public SqsMasterDataEventPublisher(SqsClient sqs, String queue, SqsCanonicalEventCodec codec) {
        this.sqs = Objects.requireNonNull(sqs, "sqs must not be null");
        this.queue = require(queue, "queue");
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
    }

    @Override
    public String publish(CanonicalEventEnvelope envelope) {
        var response = sqs.sendMessage(SendMessageRequest.builder()
                .queueUrl(queueUrl())
                .messageBody(codec.encode(envelope))
                .messageAttributes(MapAttributes.of(envelope))
                .build());
        return require(response.messageId(), "broker message id");
    }

    private String queueUrl() {
        return queue.contains("://") ? queue : sqs.getQueueUrl(builder -> builder.queueName(queue)).queueUrl();
    }

    private static String require(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
        return value;
    }
}
