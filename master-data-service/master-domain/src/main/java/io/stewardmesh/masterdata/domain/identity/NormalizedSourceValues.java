package io.stewardmesh.masterdata.domain.identity;

import java.util.Map;
import java.util.Objects;

/** Immutable normalized values together with the exact ruleset that produced them. */
public record NormalizedSourceValues(
        NormalizationRulesetId rulesetId, Map<String, String> values) {

    public NormalizedSourceValues {
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        values = Map.copyOf(Objects.requireNonNull(values, "values must not be null"));
    }
}
