package io.stewardmesh.agent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.LongSupplier;

/** Fail-closed supervisor that separates profile, identity, planning, and verification capabilities. */
public final class ReferenceStewardAgent {

    public static final int DEFAULT_MAX_TOOL_CALLS = 16;

    private static final Map<AgentPhase, Set<String>> ALLOWED_TOOLS = allowedTools();
    private static final Map<AgentPhase, Set<String>> REQUIRED_TOOLS = requiredTools();

    private final McpCapabilityClient capabilities;
    private final StewardReasoner reasoner;
    private final AgentSafetyBoundary safetyBoundary;
    private final int maximumToolCalls;
    private final AgentRuntimeTelemetry telemetry;
    private final LongSupplier nanoTime;

    public ReferenceStewardAgent(McpCapabilityClient capabilities, StewardReasoner reasoner) {
        this(capabilities, reasoner, DEFAULT_MAX_TOOL_CALLS, AgentRuntimeTelemetry.NOOP);
    }

    public ReferenceStewardAgent(
            McpCapabilityClient capabilities,
            StewardReasoner reasoner,
            int maximumToolCalls) {
        this(capabilities, reasoner, maximumToolCalls, AgentRuntimeTelemetry.NOOP);
    }

    public ReferenceStewardAgent(
            McpCapabilityClient capabilities,
            StewardReasoner reasoner,
            AgentRuntimeTelemetry telemetry) {
        this(capabilities, reasoner, DEFAULT_MAX_TOOL_CALLS, telemetry);
    }

    public ReferenceStewardAgent(
            McpCapabilityClient capabilities,
            StewardReasoner reasoner,
            int maximumToolCalls,
            AgentRuntimeTelemetry telemetry) {
        this(capabilities, reasoner, maximumToolCalls, telemetry, System::nanoTime);
    }

    ReferenceStewardAgent(
            McpCapabilityClient capabilities,
            StewardReasoner reasoner,
            int maximumToolCalls,
            AgentRuntimeTelemetry telemetry,
            LongSupplier nanoTime) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities must not be null");
        this.reasoner = Objects.requireNonNull(reasoner, "reasoner must not be null");
        this.safetyBoundary = new AgentSafetyBoundary();
        if (maximumToolCalls < 1 || maximumToolCalls > DEFAULT_MAX_TOOL_CALLS) {
            throw new IllegalArgumentException("maximumToolCalls must be between 1 and 16");
        }
        this.maximumToolCalls = maximumToolCalls;
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.nanoTime = Objects.requireNonNull(nanoTime, "nanoTime must not be null");
    }

    public AgentRunResult run(AgentGoal goal) {
        Objects.requireNonNull(goal, "goal must not be null");
        List<AgentObservation> observations = new ArrayList<>();
        long started = nanoTime.getAsLong();
        try {
            AgentRunResult result = execute(goal, observations);
            emit(() -> telemetry.runCompleted(observations.size(), elapsed(started)));
            return result;
        } catch (RuntimeException failure) {
            emit(() -> telemetry.runFailed(
                    failureCode(failure), observations.size(), elapsed(started)));
            throw failure;
        }
    }

    private AgentRunResult execute(AgentGoal goal, List<AgentObservation> observations) {
        AgentPhase phase = AgentPhase.PROFILE;

        while (true) {
            int remaining = maximumToolCalls - observations.size();
            AgentDirective directive = nextDirective(goal, phase, observations, remaining);
            if (directive instanceof AgentDirective.CallTool call) {
                if (remaining == 0) {
                    throw violation("TOOL_CALL_LIMIT_EXCEEDED", "agent tool-call limit is exhausted");
                }
                requireAllowed(phase, call.toolName());
                Map<String, Object> result = callTool(phase, call);
                observations.add(new AgentObservation(
                        observations.size() + 1,
                        phase,
                        call.toolName(),
                        call.decisionCode(),
                        result));
                continue;
            }
            if (directive instanceof AgentDirective.Advance advance) {
                requireEvidence(phase, observations);
                AgentPhase expected = next(phase);
                if (advance.nextPhase() != expected) {
                    throw violation("PHASE_TRANSITION_INVALID", "agent phases cannot be skipped or reversed");
                }
                phase = expected;
                continue;
            }
            requireEvidence(phase, observations);
            if (phase != AgentPhase.VERIFY) {
                throw violation("WORKFLOW_INCOMPLETE", "agent may complete only after verification");
            }
            return new AgentRunResult(
                    goal, ((AgentDirective.Complete) directive).outcomeCode(), observations);
        }
    }

    private AgentDirective nextDirective(
            AgentGoal goal,
            AgentPhase phase,
            List<AgentObservation> observations,
            int remaining) {
        long started = nanoTime.getAsLong();
        try {
            AgentDirective directive = Objects.requireNonNull(
                    reasoner.next(safetyBoundary.prepare(
                            goal, phase, observations, remaining, ALLOWED_TOOLS.get(phase))),
                    "reasoner directive must not be null");
            ModelTokenUsage usage = tokenUsage();
            emit(() -> telemetry.modelDecision(phase, "SUCCESS", elapsed(started), usage));
            return directive;
        } catch (RuntimeException failure) {
            emit(() -> telemetry.modelDecision(
                    phase, "FAILED", elapsed(started), ModelTokenUsage.unavailable()));
            throw failure;
        }
    }

    private Map<String, Object> callTool(AgentPhase phase, AgentDirective.CallTool call) {
        long started = nanoTime.getAsLong();
        try {
            Map<String, Object> result = Objects.requireNonNull(
                    capabilities.call(call.toolName(), call.arguments()),
                    "MCP capability result must not be null");
            emit(() -> telemetry.toolCall(phase, call.toolName(), "SUCCESS", elapsed(started)));
            return result;
        } catch (RuntimeException failure) {
            emit(() -> telemetry.toolCall(
                    phase, call.toolName(), failureCode(failure), elapsed(started)));
            throw failure;
        }
    }

    private ModelTokenUsage tokenUsage() {
        try {
            return Objects.requireNonNullElse(
                    reasoner.lastTokenUsage(), ModelTokenUsage.unavailable());
        } catch (RuntimeException ignored) {
            return ModelTokenUsage.unavailable();
        }
    }

    private Duration elapsed(long started) {
        return Duration.ofNanos(Math.max(0, nanoTime.getAsLong() - started));
    }

    private static String failureCode(RuntimeException failure) {
        if (failure instanceof McpClientException transport) {
            return transport.code();
        }
        if (failure instanceof AgentPolicyViolationException policy) {
            return policy.code();
        }
        if (failure instanceof ModelProviderException provider) {
            return provider.code();
        }
        return "AGENT_RUNTIME_FAILED";
    }

    private static void emit(Runnable signal) {
        try {
            signal.run();
        } catch (RuntimeException ignored) {
            // Telemetry is deliberately unable to alter governed workflow behavior.
        }
    }

    public static Set<String> allowedTools(AgentPhase phase) {
        return ALLOWED_TOOLS.get(Objects.requireNonNull(phase, "phase must not be null"));
    }

    public static Set<String> requiredTools(AgentPhase phase) {
        return REQUIRED_TOOLS.get(Objects.requireNonNull(phase, "phase must not be null"));
    }

    private static void requireAllowed(AgentPhase phase, String toolName) {
        if (!ALLOWED_TOOLS.get(phase).contains(toolName)) {
            throw violation(
                    "TOOL_NOT_ALLOWED_IN_PHASE",
                    "tool " + toolName + " is not allowed during " + phase);
        }
    }

    private static void requireEvidence(AgentPhase phase, List<AgentObservation> observations) {
        Set<String> observed = observations.stream()
                .filter(value -> value.phase() == phase)
                .map(AgentObservation::toolName)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        if (observed.isEmpty()) {
            throw violation("PHASE_EVIDENCE_MISSING", "phase cannot advance without a tool observation");
        }
        if (!observed.containsAll(REQUIRED_TOOLS.get(phase))) {
            throw violation(
                    "PHASE_REQUIRED_EVIDENCE_MISSING",
                    "phase cannot advance before all required capabilities have been observed");
        }
    }

    private static AgentPhase next(AgentPhase phase) {
        if (phase == AgentPhase.VERIFY) {
            throw violation("PHASE_TRANSITION_INVALID", "VERIFY is the final workflow phase");
        }
        return AgentPhase.values()[phase.ordinal() + 1];
    }

    private static Map<AgentPhase, Set<String>> allowedTools() {
        Map<AgentPhase, Set<String>> tools = new EnumMap<>(AgentPhase.class);
        tools.put(AgentPhase.PROFILE, Set.of(
                "profile_intake_artifact",
                "suggest_schema_mapping",
                "preview_mapped_records"));
        tools.put(AgentPhase.IDENTIFY, Set.of(
                "get_import_status",
                "find_party_candidates",
                "find_site_candidates",
                "explain_match"));
        tools.put(AgentPhase.PLAN, Set.of(
                "create_onboarding_proposal",
                "simulate_onboarding_plan"));
        tools.put(AgentPhase.VERIFY, Set.of(
                "get_action_plan"));
        return Map.copyOf(tools);
    }

    private static Map<AgentPhase, Set<String>> requiredTools() {
        Map<AgentPhase, Set<String>> tools = new EnumMap<>(AgentPhase.class);
        tools.put(AgentPhase.PROFILE, Set.of(
                "profile_intake_artifact",
                "suggest_schema_mapping",
                "preview_mapped_records"));
        tools.put(AgentPhase.IDENTIFY, Set.of(
                "get_import_status",
                "find_party_candidates",
                "find_site_candidates"));
        tools.put(AgentPhase.PLAN, Set.of(
                "create_onboarding_proposal",
                "simulate_onboarding_plan"));
        tools.put(AgentPhase.VERIFY, Set.of("get_action_plan"));
        return Map.copyOf(tools);
    }

    private static AgentPolicyViolationException violation(String code, String message) {
        return new AgentPolicyViolationException(code, message);
    }
}
