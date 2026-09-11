package io.stewardmesh.masterdata.domain.identity;

import java.util.Objects;

/** Stable identifier for the deterministic rules that produced normalized source values. */
public record NormalizationRulesetId(String value) {

    private static final int MAX_LENGTH = 64;

    public NormalizationRulesetId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty() || value.length() > MAX_LENGTH || !value.matches("^[a-z0-9]+(?:-[a-z0-9]+)*-v[0-9]+$")) {
            throw new IllegalArgumentException("normalization ruleset id must be a versioned kebab-case value");
        }
    }
}
