package io.stewardmesh.masterdata.domain.actionplan;

/** Caller-supplied identity that makes an approval decision safely repeatable. */
public record ApprovalRequestKey(String value) {

    private static final int MAX_LENGTH = 128;

    public ApprovalRequestKey {
        value = ActionPlanText.requireNonBlank(value, "approval request key", MAX_LENGTH);
    }
}
