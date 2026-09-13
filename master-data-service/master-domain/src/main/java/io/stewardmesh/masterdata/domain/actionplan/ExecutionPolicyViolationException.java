package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Objects;

/** A deterministic execution rule refused to apply an action plan. */
public final class ExecutionPolicyViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;
    private final ExecutionPolicyCode code;

    public ExecutionPolicyViolationException(ExecutionPolicyCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public ExecutionPolicyCode code() {
        return code;
    }
}
