package io.stewardmesh.masterdata.application.messaging;

import java.util.Objects;

public record OutboxPublication(OutboxEvent event, int attempt) {
    public OutboxPublication {
        Objects.requireNonNull(event, "event must not be null");
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
    }
}
