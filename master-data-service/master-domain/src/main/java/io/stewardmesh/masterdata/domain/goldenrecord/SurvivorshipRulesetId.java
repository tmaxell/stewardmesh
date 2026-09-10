package io.stewardmesh.masterdata.domain.goldenrecord;

import java.util.Objects;

/** Stable versioned identity of the rules used to calculate a golden record. */
public record SurvivorshipRulesetId(String value) {

    private static final int MAX_LENGTH = 64;

    public SurvivorshipRulesetId {
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty()
                || value.length() > MAX_LENGTH
                || !value.matches("^[a-z0-9]+(?:-[a-z0-9]+)*-v[0-9]+$")) {
            throw new IllegalArgumentException(
                    "survivorship ruleset id must be a versioned kebab-case value");
        }
    }
}
