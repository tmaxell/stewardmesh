package io.stewardmesh.agent;

import java.util.List;
import java.util.Objects;

/** Immutable context presented to a model-specific or deterministic reasoner adapter. */
public record AgentSnapshot(
        AgentGoal goal,
        AgentPhase phase,
        List<AgentObservation> observations,
        int remainingToolCalls) {

    public AgentSnapshot {
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        observations = List.copyOf(Objects.requireNonNull(observations, "observations must not be null"));
        if (remainingToolCalls < 0) {
            throw new IllegalArgumentException("remainingToolCalls must not be negative");
        }
    }
}
