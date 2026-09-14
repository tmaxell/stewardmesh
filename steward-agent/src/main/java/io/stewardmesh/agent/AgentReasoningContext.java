package io.stewardmesh.agent;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Structured reasoner input with physically separate trusted control and untrusted evidence. */
public record AgentReasoningContext(
        TrustedAgentPolicy policy,
        AgentGoal goal,
        AgentPhase phase,
        Set<String> allowedTools,
        List<UntrustedToolEvidence> evidence,
        int remainingToolCalls) {

    public AgentReasoningContext {
        Objects.requireNonNull(policy, "policy must not be null");
        Objects.requireNonNull(goal, "goal must not be null");
        Objects.requireNonNull(phase, "phase must not be null");
        allowedTools = Set.copyOf(Objects.requireNonNull(allowedTools, "allowedTools must not be null"));
        evidence = List.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        if (remainingToolCalls < 0) {
            throw new IllegalArgumentException("remainingToolCalls must not be negative");
        }
    }
}
