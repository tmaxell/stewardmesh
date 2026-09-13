package io.stewardmesh.masterdata.messaging.sqs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import io.stewardmesh.masterdata.application.messaging.IngressOutcome;
import io.stewardmesh.masterdata.application.messaging.IngressResult;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.CreateQueueRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import tools.jackson.databind.ObjectMapper;

@Testcontainers
class SqsMessagingAdapterIT {
    @Container
    static final GenericContainer<?> LOCALSTACK = new GenericContainer<>(
            DockerImageName.parse("localstack/localstack:4.14.0"))
            .withEnv("SERVICES", "sqs")
            .withExposedPorts(4566)
            .waitingFor(Wait.forHttp("/_localstack/health").forStatusCode(200));

    private static SqsClient sqs;
    private static String sourceQueue;
    private static String masterQueue;
    private static SqsCanonicalEventCodec codec;

    @BeforeAll
    static void createQueues() {
        URI endpoint = URI.create("http://" + LOCALSTACK.getHost() + ':' + LOCALSTACK.getMappedPort(4566));
        sqs = SqsClient.builder()
                .region(Region.US_EAST_1)
                .endpointOverride(endpoint)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("test", "test")))
                .httpClientBuilder(ApacheHttpClient.builder())
                .build();
        sourceQueue = sqs.createQueue(CreateQueueRequest.builder()
                .queueName("synthetic-source-events").build()).queueUrl();
        masterQueue = sqs.createQueue(CreateQueueRequest.builder()
                .queueName("synthetic-master-events").build()).queueUrl();
        codec = new SqsCanonicalEventCodec(new ObjectMapper());
    }

    @AfterAll
    static void closeClient() {
        if (sqs != null) sqs.close();
    }

    @Test
    void publishesCanonicalMasterEnvelopeToRealSqs() {
        var event = event(UUID.fromString("00000000-0000-0000-0000-000000001101"), "synthetic-distributor");
        String messageId = new SqsMasterDataEventPublisher(sqs, masterQueue, codec).publish(event);

        var messages = sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(masterQueue).maxNumberOfMessages(1).waitTimeSeconds(2).build()).messages();
        assertEquals(1, messages.size());
        assertEquals(event, codec.decode(messages.getFirst().body()));
        assertTrue(messageId.length() > 10);
    }

    @Test
    void receivesAuthorizedProducerAndAcknowledgesOnlyAfterResolution() {
        var calls = new AtomicInteger();
        var event = event(UUID.fromString("00000000-0000-0000-0000-000000001102"), "synthetic-distributor");
        sqs.sendMessage(SendMessageRequest.builder().queueUrl(sourceQueue).messageBody(codec.encode(event)).build());
        var consumer = new SqsReferenceDataConsumer(
                sqs, sourceQueue, codec, envelope -> {
                    calls.incrementAndGet();
                    return new IngressResult(envelope.eventId(), IngressOutcome.ACCEPTED, "APPLIED");
                }, Set.of("synthetic-distributor"), 64 * 1024);

        assertEquals(1, consumer.receiveOnce(10));
        assertEquals(1, calls.get());
        assertEquals(0, sqs.receiveMessage(ReceiveMessageRequest.builder()
                .queueUrl(sourceQueue).waitTimeSeconds(1).build()).messages().size());
    }

    private static CanonicalEventEnvelope event(UUID eventId, String producer) {
        Instant occurred = Instant.parse("2026-09-13T11:00:00Z");
        return new CanonicalEventEnvelope(
                eventId, "BusinessUnitReferenceChanged", 1, "BUSINESS_UNIT",
                "00000000-0000-0000-0000-000000001199", 1, "SYNTHETIC_ERP", producer, "sqs",
                occurred, occurred.plusSeconds(1), UUID.fromString("00000000-0000-0000-0000-000000001198"),
                Optional.empty(), "synthetic-trace-1101", CanonicalEventEnvelope.DataClassification.INTERNAL,
                Map.of("code", "SYNTHETIC-1101"));
    }
}
