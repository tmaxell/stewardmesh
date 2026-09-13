package io.stewardmesh.agent;

import java.util.Map;
import java.util.Objects;

/** Observable tool interaction retained for evaluation and operational audit. */
public record AgentObservation(
        int sequence,
        AgentPhase phase,
        String toolName,
        String decisionCode,
        Map<String, Object> result) {

    public AgentObservation {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(decisionCode, "decisionCode must not be null");
        result = Map.copyOf(Objects.requireNonNull(result, "result must not be null"));
    }
}
