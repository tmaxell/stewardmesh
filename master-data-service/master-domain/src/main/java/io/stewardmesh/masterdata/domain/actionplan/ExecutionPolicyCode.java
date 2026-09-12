package io.stewardmesh.masterdata.domain.actionplan;

/** Stable reason for refusing governed execution. */
public enum ExecutionPolicyCode {
    PLAN_NOT_APPROVED,
    PLAN_BINDING_MISMATCH,
    APPROVAL_MISSING_OR_STALE,
    EXECUTION_NOT_AUTHORIZED,
    PRECONDITION_FAILED
}
