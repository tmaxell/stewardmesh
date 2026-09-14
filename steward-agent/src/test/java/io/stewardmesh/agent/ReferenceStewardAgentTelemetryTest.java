package io.stewardmesh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class ReferenceStewardAgentTelemetryTest {

    private static final AgentGoal GOAL = new AgentGoal(
            UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a"),
            "Evaluate a synthetic onboarding flow");

    @Test
    void emitsBoundedModelToolAndRunMeasurements() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        Deque<AgentDirective> script = successfulWorkflow();
        StewardReasoner reasoner = new StewardReasoner() {
            @Override
            public AgentDirective next(AgentReasoningContext context) {
                return script.removeFirst();
            }

            @Override
            public ModelTokenUsage lastTokenUsage() {
                return ModelTokenUsage.measured(2, 1);
            }
        };
        AtomicLong time = new AtomicLong();
        var agent = new ReferenceStewardAgent(
                (tool, arguments) -> Map.of("status", "synthetic"),
                reasoner,
                16,
                telemetry,
                () -> time.getAndAdd(10));

        AgentRunResult result = agent.run(GOAL);

        assertEquals("READY_FOR_REVIEW", result.outcomeCode());
        assertEquals(13, telemetry.modelCalls.size());
        assertEquals(9, telemetry.toolCalls.size());
        assertEquals(List.of("9"), telemetry.completedRuns);
        assertEquals(39, telemetry.totalTokens);
        assertEquals(0, telemetry.failedRuns.size());
    }

    @Test
    void emitsStableFailureCodesAndTelemetryCannotChangeTheWorkflow() {
        RecordingTelemetry telemetry = new RecordingTelemetry();
        var rejected = new ReferenceStewardAgent(
                (tool, arguments) -> Map.of(),
                ignored -> new AgentDirective.CallTool(
                        "execute_approved_plan", Map.of(), "UNSAFE"),
                16,
                telemetry,
                new AtomicLong()::getAndIncrement);

        AgentPolicyViolationException rejection = assertThrows(
                AgentPolicyViolationException.class, () -> rejected.run(GOAL));

        assertEquals("TOOL_NOT_ALLOWED_IN_PHASE", rejection.code());
        assertEquals(List.of("TOOL_NOT_ALLOWED_IN_PHASE:0"), telemetry.failedRuns);

        AgentRuntimeTelemetry brokenTelemetry = new AgentRuntimeTelemetry() {
            @Override
            public void modelDecision(
                    AgentPhase phase,
                    String outcome,
                    Duration latency,
                    ModelTokenUsage tokenUsage) {
                throw new IllegalStateException("synthetic telemetry outage");
            }

            @Override
            public void toolCall(
                    AgentPhase phase, String toolName, String outcome, Duration latency) {
                throw new IllegalStateException("synthetic telemetry outage");
            }

            @Override
            public void runCompleted(int toolCalls, Duration latency) {
                throw new IllegalStateException("synthetic telemetry outage");
            }
        };
        Deque<AgentDirective> script = successfulWorkflow();
        var successful = new ReferenceStewardAgent(
                (tool, arguments) -> Map.of(),
                ignored -> script.removeFirst(),
                16,
                brokenTelemetry);

        assertEquals("READY_FOR_REVIEW", successful.run(GOAL).outcomeCode());
    }

    @Test
    void distinguishesUnavailableUsageFromMeasuredZeroTokens() {
        assertFalse(ModelTokenUsage.unavailable().available());
        assertEquals(ModelTokenUsage.measured(0, 0), new ModelTokenUsage(true, 0, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ModelTokenUsage(false, 1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> ModelTokenUsage.measured(-1, 0));
    }

    private static Deque<AgentDirective> successfulWorkflow() {
        return new ArrayDeque<>(List.of(
                call("profile_intake_artifact"),
                call("suggest_schema_mapping"),
                call("preview_mapped_records"),
                advance(AgentPhase.IDENTIFY),
                call("get_import_status"),
                call("find_party_candidates"),
                call("find_site_candidates"),
                advance(AgentPhase.PLAN),
                call("create_onboarding_proposal"),
                call("simulate_onboarding_plan"),
                advance(AgentPhase.VERIFY),
                call("get_action_plan"),
                new AgentDirective.Complete("READY_FOR_REVIEW")));
    }

    private static AgentDirective.CallTool call(String tool) {
        return new AgentDirective.CallTool(tool, Map.of("fixture", "synthetic"), "EVIDENCE");
    }

    private static AgentDirective.Advance advance(AgentPhase phase) {
        return new AgentDirective.Advance(phase, "ADVANCE");
    }

    private static final class RecordingTelemetry implements AgentRuntimeTelemetry {
        private final List<String> modelCalls = new ArrayList<>();
        private final List<String> toolCalls = new ArrayList<>();
        private final List<String> completedRuns = new ArrayList<>();
        private final List<String> failedRuns = new ArrayList<>();
        private long totalTokens;

        @Override
        public void modelDecision(
                AgentPhase phase,
                String outcome,
                Duration latency,
                ModelTokenUsage tokenUsage) {
            modelCalls.add(phase + ":" + outcome + ":" + latency.toNanos());
            totalTokens += tokenUsage.inputTokens() + tokenUsage.outputTokens();
        }

        @Override
        public void toolCall(
                AgentPhase phase, String toolName, String outcome, Duration latency) {
            toolCalls.add(phase + ":" + toolName + ":" + outcome + ":" + latency.toNanos());
        }

        @Override
        public void runCompleted(int toolCalls, Duration latency) {
            completedRuns.add(Integer.toString(toolCalls));
        }

        @Override
        public void runFailed(String failureCode, int toolCalls, Duration latency) {
            failedRuns.add(failureCode + ":" + toolCalls);
        }
    }
}
