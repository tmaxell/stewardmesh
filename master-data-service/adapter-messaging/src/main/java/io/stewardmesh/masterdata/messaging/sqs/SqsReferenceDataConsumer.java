package io.stewardmesh.masterdata.messaging.sqs;

import io.stewardmesh.masterdata.application.port.in.ConsumeReferenceDataEvent;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.Set;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

/** Bounded pull consumer; deletes only events durably resolved by the inbox use case. */
public final class SqsReferenceDataConsumer {
    private final SqsClient sqs;
    private final String queueUrl;
    private final SqsCanonicalEventCodec codec;
    private final ConsumeReferenceDataEvent consumer;
    private final Set<String> allowedProducers;
    private final int maximumBytes;

    public SqsReferenceDataConsumer(
            SqsClient sqs,
            String queueUrl,
            SqsCanonicalEventCodec codec,
            ConsumeReferenceDataEvent consumer,
            Set<String> allowedProducers,
            int maximumBytes) {
        this.sqs = Objects.requireNonNull(sqs, "sqs must not be null");
        this.queueUrl = require(queueUrl);
        this.codec = Objects.requireNonNull(codec, "codec must not be null");
        this.consumer = Objects.requireNonNull(consumer, "consumer must not be null");
        this.allowedProducers = Set.copyOf(Objects.requireNonNull(allowedProducers, "allowedProducers must not be null"));
        if (allowedProducers.isEmpty()) throw new IllegalArgumentException("allowedProducers must not be empty");
        if (maximumBytes < 1024 || maximumBytes > 262_144) throw new IllegalArgumentException("invalid maximumBytes");
        this.maximumBytes = maximumBytes;
    }

    public int receiveOnce(int batchSize) {
        if (batchSize < 1 || batchSize > 10) throw new IllegalArgumentException("batchSize must be between 1 and 10");
        var messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl).maxNumberOfMessages(batchSize).waitTimeSeconds(0).build())
                .messages();
        int resolved = 0;
        for (var message : messages) {
            if (message.body().getBytes(StandardCharsets.UTF_8).length > maximumBytes) continue;
            var envelope = codec.decode(message.body());
            if (!allowedProducers.contains(envelope.producer())) continue;
            consumer.execute(envelope);
            sqs.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl).receiptHandle(message.receiptHandle()).build());
            resolved++;
        }
        return resolved;
    }

    private static String require(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("queueUrl must not be blank");
        return value;
    }
}
