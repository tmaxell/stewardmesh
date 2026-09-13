package io.stewardmesh.agent;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Fail-closed supervisor that separates profile, identity, planning, and verification capabilities. */
public final class ReferenceStewardAgent {

    public static final int DEFAULT_MAX_TOOL_CALLS = 16;

    private static final Map<AgentPhase, Set<String>> ALLOWED_TOOLS = allowedTools();

    private final McpCapabilityClient capabilities;
    private final StewardReasoner reasoner;
    private final AgentSafetyBoundary safetyBoundary;
    private final int maximumToolCalls;

    public ReferenceStewardAgent(McpCapabilityClient capabilities, StewardReasoner reasoner) {
        this(capabilities, reasoner, DEFAULT_MAX_TOOL_CALLS);
    }

    public ReferenceStewardAgent(
            McpCapabilityClient capabilities,
            StewardReasoner reasoner,
            int maximumToolCalls) {
        this.capabilities = Objects.requireNonNull(capabilities, "capabilities must not be null");
        this.reasoner = Objects.requireNonNull(reasoner, "reasoner must not be null");
        this.safetyBoundary = new AgentSafetyBoundary();
        if (maximumToolCalls < 1 || maximumToolCalls > DEFAULT_MAX_TOOL_CALLS) {
            throw new IllegalArgumentException("maximumToolCalls must be between 1 and 16");
        }
        this.maximumToolCalls = maximumToolCalls;
    }

    public AgentRunResult run(AgentGoal goal) {
        Objects.requireNonNull(goal, "goal must not be null");
        List<AgentObservation> observations = new ArrayList<>();
        AgentPhase phase = AgentPhase.PROFILE;

        while (true) {
            int remaining = maximumToolCalls - observations.size();
            AgentDirective directive = Objects.requireNonNull(
                    reasoner.next(safetyBoundary.prepare(
                            goal, phase, observations, remaining, ALLOWED_TOOLS.get(phase))),
                    "reasoner directive must not be null");
            if (directive instanceof AgentDirective.CallTool call) {
                if (remaining == 0) {
                    throw violation("TOOL_CALL_LIMIT_EXCEEDED", "agent tool-call limit is exhausted");
                }
                requireAllowed(phase, call.toolName());
                Map<String, Object> result = Objects.requireNonNull(
                        capabilities.call(call.toolName(), call.arguments()),
                        "MCP capability result must not be null");
                observations.add(new AgentObservation(
                        observations.size() + 1,
                        phase,
                        call.toolName(),
                        call.decisionCode(),
                        result));
                continue;
            }
            if (directive instanceof AgentDirective.Advance advance) {
                requireObserved(phase, observations);
                AgentPhase expected = next(phase);
                if (advance.nextPhase() != expected) {
                    throw violation("PHASE_TRANSITION_INVALID", "agent phases cannot be skipped or reversed");
                }
                phase = expected;
                continue;
            }
            requireObserved(phase, observations);
            if (phase != AgentPhase.VERIFY) {
                throw violation("WORKFLOW_INCOMPLETE", "agent may complete only after verification");
            }
            return new AgentRunResult(
                    goal, ((AgentDirective.Complete) directive).outcomeCode(), observations);
        }
    }

    public static Set<String> allowedTools(AgentPhase phase) {
        return ALLOWED_TOOLS.get(Objects.requireNonNull(phase, "phase must not be null"));
    }

    private static void requireAllowed(AgentPhase phase, String toolName) {
        if (!ALLOWED_TOOLS.get(phase).contains(toolName)) {
            throw violation(
                    "TOOL_NOT_ALLOWED_IN_PHASE",
                    "tool " + toolName + " is not allowed during " + phase);
        }
    }

    private static void requireObserved(AgentPhase phase, List<AgentObservation> observations) {
        boolean observed = observations.stream().anyMatch(value -> value.phase() == phase);
        if (!observed) {
            throw violation("PHASE_EVIDENCE_MISSING", "phase cannot advance without a tool observation");
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
        tools.put(AgentPhase.PROFILE, Set.of("profile_intake_artifact"));
        tools.put(AgentPhase.IDENTIFY, Set.of(
                "get_import_status",
                "find_party_candidates",
                "find_site_candidates",
                "explain_match"));
        tools.put(AgentPhase.PLAN, Set.of(
                "create_onboarding_proposal",
                "simulate_onboarding_plan"));
        tools.put(AgentPhase.VERIFY, Set.of(
                "get_action_plan",
                "verify_onboarding_result"));
        return Map.copyOf(tools);
    }

    private static AgentPolicyViolationException violation(String code, String message) {
        return new AgentPolicyViolationException(code, message);
    }
}
