package io.stewardmesh.agent.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AgentEvalGraderTest {

    @Test
    void reportsPerfectMetricsForConformingObservableOutcomes() throws Exception {
        FrozenEvalDataset dataset = dataset();
        List<AgentEvalActual> actuals = dataset.scenarios().stream()
                .map(AgentEvalGraderTest::conformingActual)
                .toList();

        AgentEvalReport report = new AgentEvalGrader().grade(dataset, actuals);

        assertEquals(18, report.scenarioCount());
        assertEquals(18, report.successfulScenarioCount());
        assertEquals(1.0, report.taskSuccessRate());
        assertEquals(1.0, report.correctClassificationRate());
        assertEquals(1.0, report.duplicatePrecision());
        assertEquals(1.0, report.duplicateRecall());
        assertEquals(0.0, report.unsafeActionRate());
        assertEquals(1.0, report.requiredEvidenceCompleteness());
        assertEquals(0.0, report.unnecessaryEscalationRate());
        assertEquals(1.0, report.recoveryWithoutRepeatedSideEffectRate());
        assertEquals(16, report.maximumToolCallsObserved());
        assertEquals(180, report.totalCostMicrounits());
        assertTrue(report.scenarioResults().stream().allMatch(AgentEvalScenarioResult::successful));
    }

    @Test
    void exposesSafetyEvidenceEscalationAndRecoveryRegressions() throws Exception {
        FrozenEvalDataset dataset = dataset();
        List<AgentEvalActual> actuals = new ArrayList<>(dataset.scenarios().stream()
                .map(AgentEvalGraderTest::conformingActual)
                .toList());
        FrozenEvalScenario scenario = dataset.scenarios().getFirst();
        ExpectedEvalOutcome expected = scenario.expected();
        actuals.set(0, new AgentEvalActual(
                scenario.id(),
                "WRONG_OUTCOME",
                "WRONG_CLASSIFICATION",
                false,
                true,
                true,
                true,
                false,
                expected.maximumToolCalls() + 1,
                expected.maximumSideEffects() + 1,
                25,
                20,
                Set.of()));

        AgentEvalReport report = new AgentEvalGrader().grade(dataset, actuals);

        AgentEvalScenarioResult result = report.scenarioResults().getFirst();
        assertEquals(17, report.successfulScenarioCount());
        assertEquals(1.0 / 18, report.unsafeActionRate());
        assertEquals(1.0 / 18, report.unnecessaryEscalationRate());
        assertTrue(result.failures().containsAll(List.of(
                "TASK_COMPLETION_MISMATCH",
                "OUTCOME_MISMATCH",
                "CLASSIFICATION_MISMATCH",
                "DUPLICATE_DECISION_MISMATCH",
                "ESCALATION_MISMATCH",
                "UNSAFE_ACTION_MISMATCH",
                "TOOL_CALL_BUDGET_EXCEEDED",
                "SIDE_EFFECT_BUDGET_EXCEEDED",
                "REQUIRED_EVIDENCE_MISSING")));
    }

    @Test
    void rejectsMissingDuplicateAndUnknownActuals() throws Exception {
        FrozenEvalDataset dataset = dataset();
        AgentEvalActual first = conformingActual(dataset.scenarios().getFirst());
        assertThrows(
                IllegalArgumentException.class,
                () -> new AgentEvalGrader().grade(dataset, List.of(first)));
        List<AgentEvalActual> duplicates = new ArrayList<>(dataset.scenarios().stream()
                .map(AgentEvalGraderTest::conformingActual)
                .toList());
        duplicates.set(1, first);
        assertThrows(
                IllegalArgumentException.class,
                () -> new AgentEvalGrader().grade(dataset, duplicates));
    }

    private FrozenEvalDataset dataset() throws Exception {
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/evals/frozen-agent-scenarios-v1.json"))) {
            return new FrozenEvalDatasetLoader().load(input);
        }
    }

    private static AgentEvalActual conformingActual(FrozenEvalScenario scenario) {
        ExpectedEvalOutcome expected = scenario.expected();
        return new AgentEvalActual(
                scenario.id(),
                expected.outcomeCode(),
                expected.classification(),
                expected.taskSuccess(),
                expected.duplicate(),
                expected.escalationRequired(),
                expected.unsafeAction(),
                expected.recoveryRequired(),
                expected.maximumToolCalls(),
                expected.maximumSideEffects(),
                10,
                10,
                Set.copyOf(expected.requiredEvidence()));
    }
}
