package io.stewardmesh.agent.eval;

import io.stewardmesh.agent.AgentObservation;
import io.stewardmesh.agent.AgentRunResult;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Converts a reference-agent audit trace into the provider-neutral eval result schema. */
public final class ReferenceAgentEvalAdapter {

    private static final Map<String, String> TOOL_EVIDENCE = Map.ofEntries(
            Map.entry("profile_intake_artifact", "INTAKE_PROFILE"),
            Map.entry("preview_mapped_records", "MAPPING_PREVIEW"),
            Map.entry("get_import_status", "VALIDATION_ISSUES"),
            Map.entry("find_party_candidates", "PARTY_CANDIDATES"),
            Map.entry("find_site_candidates", "SITE_CANDIDATES"),
            Map.entry("explain_match", "MATCH_EXPLANATION"),
            Map.entry("simulate_onboarding_plan", "PLAN_SIMULATION"),
            Map.entry("get_action_plan", "SEALED_PLAN"));

    public AgentEvalActual adapt(
            String scenarioId,
            AgentRunResult run,
            AgentEvalMeasurements measurements) {
        Objects.requireNonNull(run, "run must not be null");
        Objects.requireNonNull(measurements, "measurements must not be null");
        String classification = classification(run);
        Set<String> evidence = new LinkedHashSet<>();
        run.observations().stream()
                .map(AgentObservation::toolName)
                .map(TOOL_EVIDENCE::get)
                .filter(Objects::nonNull)
                .forEach(evidence::add);
        return new AgentEvalActual(
                scenarioId,
                run.outcomeCode(),
                classification,
                true,
                classification.endsWith("DUPLICATE"),
                measurements.escalated(),
                measurements.unsafeAction(),
                measurements.recoverySucceeded(),
                run.observations().size(),
                measurements.sideEffects(),
                measurements.latencyMillis(),
                measurements.costMicrounits(),
                evidence);
    }

    private static String classification(AgentRunResult run) {
        return run.observations().reversed().stream()
                .map(AgentObservation::result)
                .map(result -> result.get("classification"))
                .filter(String.class::isInstance)
                .map(String.class::cast)
                .map(String::strip)
                .filter(value -> !value.isEmpty())
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "agent trace must contain an observable classification"));
    }
}
