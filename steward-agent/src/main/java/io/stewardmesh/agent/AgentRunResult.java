package io.stewardmesh.agent;

import java.util.List;
import java.util.Objects;

/** Completed supervisor trace containing decisions and tool outcomes but no hidden reasoning. */
public record AgentRunResult(
        AgentGoal goal,
        String outcomeCode,
        List<AgentObservation> observations) {

    public AgentRunResult {
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(outcomeCode, "outcomeCode must not be null");
        observations = List.copyOf(Objects.requireNonNull(observations, "observations must not be null"));
    }
}
