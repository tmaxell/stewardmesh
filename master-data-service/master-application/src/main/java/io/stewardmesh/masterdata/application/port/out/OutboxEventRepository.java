package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.messaging.OutboxEvent;

@FunctionalInterface
public interface OutboxEventRepository {

    void append(OutboxEvent event);
}
