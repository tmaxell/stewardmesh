package io.stewardmesh.masterdata.domain.actionplan;

import java.time.Instant;
import java.util.Objects;

/** Immutable human decision bound to the exact version and hash of a sealed action plan. */
public record ActionPlanApproval(
        ActionPlanId planId,
        ActionPlanVersion planVersion,
        ActionPlanHash planHash,
        ApprovalDecision decision,
        ApprovalRequestKey requestKey,
        String decidedBySubject,
        Instant decidedAt,
        String reason) {

    private static final int MAX_SUBJECT_LENGTH = 128;
    private static final int MAX_REASON_LENGTH = 512;

    public ActionPlanApproval {
        Objects.requireNonNull(planId, "plan id must not be null");
        Objects.requireNonNull(planVersion, "plan version must not be null");
        Objects.requireNonNull(planHash, "plan hash must not be null");
        Objects.requireNonNull(decision, "approval decision must not be null");
        Objects.requireNonNull(requestKey, "approval request key must not be null");
        decidedBySubject = ActionPlanText.requireNonBlank(
                decidedBySubject, "decided by subject", MAX_SUBJECT_LENGTH);
        Objects.requireNonNull(decidedAt, "decided at must not be null");
        reason = ActionPlanText.requireNonBlank(reason, "approval reason", MAX_REASON_LENGTH);
    }

    public ActionPlanStatus resultingStatus() {
        return decision == ApprovalDecision.APPROVE
                ? ActionPlanStatus.APPROVED
                : ActionPlanStatus.REJECTED;
    }
}
