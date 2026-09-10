package io.stewardmesh.masterdata.domain.goldenrecord;

import java.util.Objects;

/** One selected canonical value together with its source and rule evidence. */
public record GoldenAttribute(
        GoldenAttributeName name, String value, GoldenAttributeProvenance provenance) {

    private static final int MAX_VALUE_LENGTH = 2_048;

    public GoldenAttribute {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(value, "value must not be null");
        value = value.trim();
        if (value.isEmpty() || value.length() > MAX_VALUE_LENGTH) {
            throw new IllegalArgumentException("golden attribute value must be non-blank and bounded");
        }
        Objects.requireNonNull(provenance, "provenance must not be null");
    }
}
