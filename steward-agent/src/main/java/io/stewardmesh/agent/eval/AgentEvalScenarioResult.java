package io.stewardmesh.agent.eval;

import java.util.List;
import java.util.Objects;

/** Deterministic pass/fail result for one frozen scenario. */
public record AgentEvalScenarioResult(String scenarioId, boolean successful, List<String> failures) {

    public AgentEvalScenarioResult {
        scenarioId = FrozenEvalDataset.requireText(scenarioId, "scenarioId");
        failures = List.copyOf(Objects.requireNonNull(failures, "failures must not be null"));
        if (successful == !failures.isEmpty()) {
            throw new IllegalArgumentException("successful must agree with failures");
        }
    }
}
