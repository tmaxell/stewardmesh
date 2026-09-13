package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Objects;

/** A deterministic approval rule rejected the requested decision. */
public final class ApprovalPolicyViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ApprovalPolicyCode code;

    public ApprovalPolicyViolationException(ApprovalPolicyCode code, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code must not be null");
    }

    public ApprovalPolicyCode code() {
        return code;
    }
}
