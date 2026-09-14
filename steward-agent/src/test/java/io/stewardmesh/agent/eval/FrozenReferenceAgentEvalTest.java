package io.stewardmesh.agent.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.stewardmesh.agent.AgentDirective;
import io.stewardmesh.agent.AgentGoal;
import io.stewardmesh.agent.AgentPhase;
import io.stewardmesh.agent.ReferenceStewardAgent;
import java.io.InputStream;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FrozenReferenceAgentEvalTest {

    @Test
    void referenceSupervisorPassesThreeFrozenIdentityOutcomesEndToEnd() throws Exception {
        FrozenEvalDataset dataset = dataset();
        List<AgentEvalActual> actuals = new ArrayList<>();
        actuals.add(run("new-supplier", "NEW_PARTY", "PROPOSAL_READY_FOR_HUMAN_REVIEW", false, false));
        actuals.add(run("exact-duplicate", "EXACT_DUPLICATE", "EXACT_DUPLICATE_CONFIRMED", false, true));
        actuals.add(run("fuzzy-duplicate", "FUZZY_DUPLICATE", "FUZZY_DUPLICATE_ESCALATED", true, true));

        FrozenEvalDataset selected = new FrozenEvalDataset(
                dataset.datasetId(),
                dataset.version(),
                dataset.frozen(),
                dataset.dataPolicy(),
                dataset.scenarios().stream()
                        .filter(scenario -> actuals.stream()
                                .anyMatch(actual -> actual.scenarioId().equals(scenario.id())))
                        .toList());
        AgentEvalReport report = new AgentEvalGrader().grade(selected, actuals);

        assertEquals(3, report.successfulScenarioCount());
        assertEquals(1.0, report.taskSuccessRate());
        assertEquals(1.0, report.correctClassificationRate());
        assertEquals(1.0, report.duplicatePrecision());
        assertEquals(1.0, report.duplicateRecall());
        assertEquals(0.0, report.unsafeActionRate());
        assertEquals(1.0, report.requiredEvidenceCompleteness());
    }

    private static AgentEvalActual run(
            String scenarioId,
            String classification,
            String outcome,
            boolean escalated,
            boolean explainMatch) {
        Deque<AgentDirective> script = workflow(outcome, explainMatch);
        UUID importId = UUID.nameUUIDFromBytes(("synthetic/" + scenarioId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        AgentGoal goal = new AgentGoal(importId, "Evaluate synthetic scenario " + scenarioId);
        var agent = new ReferenceStewardAgent(
                (tool, arguments) -> Map.of("classification", classification, "fixture", scenarioId),
                ignored -> script.removeFirst());
        AgentEvalMeasurements measurements = new AgentEvalMeasurements(
                escalated, false, false, scenarioId.equals("new-supplier") ? 1 : 0, 10, 25);
        return new ReferenceAgentEvalAdapter().adapt(scenarioId, agent.run(goal), measurements);
    }

    private static Deque<AgentDirective> workflow(String outcome, boolean explainMatch) {
        List<AgentDirective> directives = new ArrayList<>(List.of(
                call("profile_intake_artifact"),
                call("suggest_schema_mapping"),
                call("preview_mapped_records"),
                advance(AgentPhase.IDENTIFY),
                call("get_import_status"),
                call("find_party_candidates"),
                call("find_site_candidates")));
        if (explainMatch) {
            directives.add(call("explain_match"));
        }
        directives.addAll(List.of(
                advance(AgentPhase.PLAN),
                call("create_onboarding_proposal"),
                call("simulate_onboarding_plan"),
                advance(AgentPhase.VERIFY),
                call("get_action_plan"),
                new AgentDirective.Complete(outcome)));
        return new ArrayDeque<>(directives);
    }

    private FrozenEvalDataset dataset() throws Exception {
        try (InputStream input = Objects.requireNonNull(
                getClass().getResourceAsStream("/evals/frozen-agent-scenarios-v1.json"))) {
            return new FrozenEvalDatasetLoader().load(input);
        }
    }

    private static AgentDirective.CallTool call(String tool) {
        return new AgentDirective.CallTool(tool, Map.of("fixture", "synthetic"), "EVIDENCE_COLLECTED");
    }

    private static AgentDirective.Advance advance(AgentPhase phase) {
        return new AgentDirective.Advance(phase, "PHASE_COMPLETE");
    }
}
