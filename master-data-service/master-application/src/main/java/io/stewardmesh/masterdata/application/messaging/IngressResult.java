package io.stewardmesh.masterdata.application.messaging;

import java.util.Objects;
import java.util.UUID;

public record IngressResult(UUID eventId, IngressOutcome outcome, String reasonCode) {
    public IngressResult {
        Objects.requireNonNull(eventId, "eventId must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
    }
}
