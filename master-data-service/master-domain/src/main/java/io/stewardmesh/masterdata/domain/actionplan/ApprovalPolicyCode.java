package io.stewardmesh.masterdata.domain.actionplan;

/** Stable deterministic outcomes exposed when an approval request is refused. */
public enum ApprovalPolicyCode {
    PLAN_NOT_PROPOSED,
    PLAN_BINDING_MISMATCH,
    APPROVAL_NOT_AUTHORIZED,
    SELF_APPROVAL_FORBIDDEN
}
