package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.port.in.PublishMasterDataEvents;
import io.stewardmesh.masterdata.messaging.sqs.SqsReferenceDataConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "stewardmesh.messaging.enabled", havingValue = "true")
final class MessagingRelayScheduler {
    private final SqsReferenceDataConsumer inbound;
    private final PublishMasterDataEvents outbound;
    private final int inboundBatchSize;
    private final int outboundBatchSize;

    MessagingRelayScheduler(
            SqsReferenceDataConsumer inbound,
            PublishMasterDataEvents outbound,
            @Value("${stewardmesh.messaging.inbound-batch-size}") int inboundBatchSize,
            @Value("${stewardmesh.messaging.outbound-batch-size}") int outboundBatchSize) {
        this.inbound = inbound;
        this.outbound = outbound;
        this.inboundBatchSize = inboundBatchSize;
        this.outboundBatchSize = outboundBatchSize;
    }

    @Scheduled(fixedDelayString = "${stewardmesh.messaging.poll-delay}")
    void consume() {
        inbound.receiveOnce(inboundBatchSize);
    }

    @Scheduled(fixedDelayString = "${stewardmesh.messaging.poll-delay}")
    void publish() {
        outbound.execute(outboundBatchSize);
    }
}
