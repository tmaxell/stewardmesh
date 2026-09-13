package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.messaging.BusinessUnitReferenceEventApplier;
import io.stewardmesh.masterdata.application.messaging.OutboxPublicationService;
import io.stewardmesh.masterdata.application.messaging.ReferenceDataEventConsumerService;
import io.stewardmesh.masterdata.application.port.in.ConsumeReferenceDataEvent;
import io.stewardmesh.masterdata.application.port.in.GetBusinessUnit;
import io.stewardmesh.masterdata.application.port.in.PublishMasterDataEvents;
import io.stewardmesh.masterdata.application.port.in.SynchronizeBusinessUnit;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.InboxEventRepository;
import io.stewardmesh.masterdata.application.port.out.MasterDataEventPublisher;
import io.stewardmesh.masterdata.application.port.out.OutboxPublicationRepository;
import io.stewardmesh.masterdata.application.port.out.QuarantineEventRepository;
import io.stewardmesh.masterdata.application.port.out.ReferenceDataEventApplier;
import io.stewardmesh.masterdata.messaging.sqs.SqsCanonicalEventCodec;
import io.stewardmesh.masterdata.messaging.sqs.SqsMasterDataEventPublisher;
import io.stewardmesh.masterdata.messaging.sqs.SqsReferenceDataConsumer;
import java.net.URI;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import tools.jackson.databind.ObjectMapper;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
class MessagingConfiguration {
    @Bean
    SqsClient messagingSqsClient(
            @Value("${stewardmesh.messaging.region}") String region,
            @Value("${stewardmesh.messaging.endpoint}") URI endpoint) {
        return SqsClient.builder()
                .region(Region.of(region))
                .endpointOverride(endpoint)
                .credentialsProvider(DefaultCredentialsProvider.builder().build())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(5)).socketTimeout(Duration.ofSeconds(20)))
                .build();
    }

    @Bean
    SqsCanonicalEventCodec canonicalEventCodec(ObjectMapper objectMapper) {
        return new SqsCanonicalEventCodec(objectMapper);
    }

    @Bean
    MasterDataEventPublisher masterDataEventPublisher(
            SqsClient sqs,
            SqsCanonicalEventCodec codec,
            @Value("${stewardmesh.messaging.master-events-queue}") String queue,
            MeterRegistry meterRegistry) {
        return new MeteredMasterDataEventPublisher(
                new SqsMasterDataEventPublisher(sqs, queue, codec), meterRegistry);
    }

    @Bean
    ReferenceDataEventApplier referenceDataEventApplier(
            GetBusinessUnit getBusinessUnit, SynchronizeBusinessUnit synchronizeBusinessUnit) {
        return new BusinessUnitReferenceEventApplier(getBusinessUnit, synchronizeBusinessUnit);
    }

    @Bean
    ConsumeReferenceDataEvent consumeReferenceDataEvent(
            InboxEventRepository inbox,
            QuarantineEventRepository quarantine,
            ReferenceDataEventApplier applier,
            ApplicationTransaction transaction,
            Clock clock,
            MeterRegistry meterRegistry) {
        return new MeteredConsumeReferenceDataEvent(
                new ReferenceDataEventConsumerService(inbox, quarantine, applier, transaction, clock),
                meterRegistry,
                clock);
    }

    @Bean
    PublishMasterDataEvents publishMasterDataEvents(
            OutboxPublicationRepository outbox,
            MasterDataEventPublisher publisher,
            Clock clock,
            @Value("${stewardmesh.messaging.claim-timeout}") Duration claimTimeout) {
        return new OutboxPublicationService(outbox, publisher, clock, claimTimeout);
    }

    @Bean
    SqsReferenceDataConsumer sqsReferenceDataConsumer(
            SqsClient sqs,
            SqsCanonicalEventCodec codec,
            ConsumeReferenceDataEvent consumer,
            @Value("${stewardmesh.messaging.source-events-queue}") String queue,
            @Value("${stewardmesh.messaging.allowed-producers}") String allowedProducers,
            @Value("${stewardmesh.messaging.maximum-message-bytes}") int maximumBytes) {
        Set<String> producers = Arrays.stream(allowedProducers.split(","))
                .map(String::strip).filter(value -> !value.isEmpty()).collect(Collectors.toUnmodifiableSet());
        return new SqsReferenceDataConsumer(sqs, queue, codec, consumer, producers, maximumBytes);
    }
}
