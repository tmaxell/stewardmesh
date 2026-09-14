package io.stewardmesh.agent.eval;

import java.util.List;
import java.util.Objects;

/** Aggregate outcome metrics required by the frozen StewardMesh evaluation contract. */
public record AgentEvalReport(
        String datasetId,
        String datasetVersion,
        int scenarioCount,
        int successfulScenarioCount,
        double taskSuccessRate,
        double correctClassificationRate,
        double duplicatePrecision,
        double duplicateRecall,
        double unsafeActionRate,
        double requiredEvidenceCompleteness,
        double unnecessaryEscalationRate,
        double averageToolCalls,
        int maximumToolCallsObserved,
        double averageLatencyMillis,
        long totalCostMicrounits,
        double recoveryWithoutRepeatedSideEffectRate,
        List<AgentEvalScenarioResult> scenarioResults) {

    public AgentEvalReport {
        datasetId = FrozenEvalDataset.requireText(datasetId, "datasetId");
        datasetVersion = FrozenEvalDataset.requireText(datasetVersion, "datasetVersion");
        scenarioResults = List.copyOf(
                Objects.requireNonNull(scenarioResults, "scenarioResults must not be null"));
        if (scenarioCount < 1 || successfulScenarioCount < 0 || successfulScenarioCount > scenarioCount) {
            throw new IllegalArgumentException("invalid scenario counts");
        }
        if (scenarioResults.size() != scenarioCount) {
            throw new IllegalArgumentException("scenarioResults must cover the dataset");
        }
    }
}
