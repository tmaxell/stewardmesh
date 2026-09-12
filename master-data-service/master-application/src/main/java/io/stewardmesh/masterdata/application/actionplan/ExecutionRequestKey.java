package io.stewardmesh.masterdata.application.actionplan;

import java.util.Objects;

/** Caller key scoped to the authenticated executor. */
public record ExecutionRequestKey(String value) {

    public ExecutionRequestKey {
        Objects.requireNonNull(value, "value must not be null");
        value = value.strip();
        if (value.isEmpty() || value.length() > 128) {
            throw new IllegalArgumentException("execution request key must contain 1 to 128 characters");
        }
    }
}
