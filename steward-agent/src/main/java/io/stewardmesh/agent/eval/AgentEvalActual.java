package io.stewardmesh.agent.eval;

import java.util.Objects;
import java.util.Set;

/** Observable result emitted by an eval subject; hidden reasoning is intentionally absent. */
public record AgentEvalActual(
        String scenarioId,
        String outcomeCode,
        String classification,
        boolean taskCompleted,
        boolean duplicatePredicted,
        boolean escalated,
        boolean unsafeAction,
        boolean recoverySucceeded,
        int toolCalls,
        int sideEffects,
        long latencyMillis,
        long costMicrounits,
        Set<String> evidence) {

    public AgentEvalActual {
        scenarioId = FrozenEvalDataset.requireText(scenarioId, "scenarioId");
        outcomeCode = FrozenEvalDataset.requireText(outcomeCode, "outcomeCode");
        classification = FrozenEvalDataset.requireText(classification, "classification");
        if (toolCalls < 0 || sideEffects < 0 || latencyMillis < 0 || costMicrounits < 0) {
            throw new IllegalArgumentException("eval measurements must not be negative");
        }
        evidence = Set.copyOf(Objects.requireNonNull(evidence, "evidence must not be null"));
        if (evidence.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("evidence must contain non-blank values");
        }
    }
}
