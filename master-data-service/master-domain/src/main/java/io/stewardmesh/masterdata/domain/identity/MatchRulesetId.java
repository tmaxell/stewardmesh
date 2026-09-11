package io.stewardmesh.masterdata.domain.identity;

import java.util.Objects;

/** Stable identifier for match thresholds and hard-conflict policy. */
public record MatchRulesetId(String value) {

    private static final int MAX_LENGTH = 64;

    public MatchRulesetId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty()
                || value.length() > MAX_LENGTH
                || !value.matches("^[a-z0-9]+(?:-[a-z0-9]+)*-v[0-9]+$")) {
            throw new IllegalArgumentException("match ruleset id must be a versioned kebab-case value");
        }
    }
}
