package io.stewardmesh.agent.eval;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** Versioned collection of synthetic, immutable evaluation expectations. */
public record FrozenEvalDataset(
        String datasetId,
        String version,
        boolean frozen,
        String dataPolicy,
        List<FrozenEvalScenario> scenarios) {

    public FrozenEvalDataset {
        datasetId = requireText(datasetId, "datasetId");
        version = requireText(version, "version");
        dataPolicy = requireText(dataPolicy, "dataPolicy");
        scenarios = List.copyOf(Objects.requireNonNull(scenarios, "scenarios must not be null"));
        if (!frozen) {
            throw new IllegalArgumentException("eval dataset must be frozen");
        }
        if (!"SYNTHETIC_ONLY".equals(dataPolicy)) {
            throw new IllegalArgumentException("eval dataset must contain synthetic data only");
        }
        if (scenarios.isEmpty()) {
            throw new IllegalArgumentException("eval dataset must contain scenarios");
        }
        Set<String> ids = new HashSet<>();
        for (FrozenEvalScenario scenario : scenarios) {
            if (!ids.add(scenario.id())) {
                throw new IllegalArgumentException("duplicate eval scenario id: " + scenario.id());
            }
        }
    }

    static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return normalized;
    }
}
