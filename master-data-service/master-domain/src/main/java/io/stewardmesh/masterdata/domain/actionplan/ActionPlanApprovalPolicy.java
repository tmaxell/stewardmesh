package io.stewardmesh.masterdata.domain.actionplan;

import java.time.Instant;
import java.util.Objects;

/**
 * Deterministic separation-of-duties policy for governed action plans. Authentication is resolved
 * by the server; the domain receives only the resulting subject and authorization decision.
 */
public final class ActionPlanApprovalPolicy {

    public ActionPlanApproval decide(
            GovernedActionPlan governed,
            ActionPlanVersion expectedVersion,
            ActionPlanHash expectedHash,
            ApprovalDecision decision,
            ApprovalRequestKey requestKey,
            String authenticatedSubject,
            boolean authorizedToApprove,
            Instant decidedAt,
            String reason) {
        Objects.requireNonNull(governed, "governed plan must not be null");
        Objects.requireNonNull(expectedVersion, "expected version must not be null");
        Objects.requireNonNull(expectedHash, "expected hash must not be null");
        Objects.requireNonNull(decision, "approval decision must not be null");
        Objects.requireNonNull(requestKey, "approval request key must not be null");
        Objects.requireNonNull(decidedAt, "decided at must not be null");
        String subject = ActionPlanText.requireNonBlank(
                authenticatedSubject, "authenticated approval subject", 128);
        String normalizedReason = ActionPlanText.requireNonBlank(reason, "approval reason", 512);

        if (governed.status() != ActionPlanStatus.PROPOSED) {
            throw violation(ApprovalPolicyCode.PLAN_NOT_PROPOSED,
                    "only a proposed action plan may receive a decision");
        }
        if (!governed.version().equals(expectedVersion) || !governed.hash().equals(expectedHash)) {
            throw violation(ApprovalPolicyCode.PLAN_BINDING_MISMATCH,
                    "approval must bind the exact sealed plan version and hash");
        }
        if (!authorizedToApprove) {
            throw violation(ApprovalPolicyCode.APPROVAL_NOT_AUTHORIZED,
                    "the authenticated principal may not approve action plans");
        }
        if (governed.plan().proposedBySubject().equals(subject)) {
            throw violation(ApprovalPolicyCode.SELF_APPROVAL_FORBIDDEN,
                    "the proposer may not decide the same action plan");
        }

        return new ActionPlanApproval(
                governed.id(), governed.version(), governed.hash(), decision, requestKey,
                subject, decidedAt, normalizedReason);
    }

    private static ApprovalPolicyViolationException violation(
            ApprovalPolicyCode code, String message) {
        return new ApprovalPolicyViolationException(code, message);
    }
}
