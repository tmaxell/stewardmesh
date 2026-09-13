package io.stewardmesh.agent;

import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Builds the only context that may cross from the supervisor into a reasoner adapter. */
final class AgentSafetyBoundary {

    AgentReasoningContext prepare(
            AgentGoal goal,
            AgentPhase phase,
            List<AgentObservation> observations,
            int remainingToolCalls,
            Set<String> allowedTools) {
        Objects.requireNonNull(observations, "observations must not be null");
        List<UntrustedToolEvidence> evidence = observations.stream()
                .map(observation -> new UntrustedToolEvidence(
                        observation.sequence(),
                        observation.phase(),
                        observation.toolName(),
                        observation.decisionCode(),
                        observation.result()))
                .toList();
        return new AgentReasoningContext(
                TrustedAgentPolicy.standard(),
                goal,
                phase,
                allowedTools,
                evidence,
                remainingToolCalls);
    }
}
