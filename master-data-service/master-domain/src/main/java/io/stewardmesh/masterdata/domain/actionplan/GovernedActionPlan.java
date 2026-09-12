package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Objects;

/**
 * Sealed plan together with the lifecycle state that decides whether it may still be approved or
 * executed. The plan itself never changes; only the governed status advances.
 */
public record GovernedActionPlan(ActionPlan plan, ActionPlanStatus status) {

    public GovernedActionPlan {
        Objects.requireNonNull(plan, "plan must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }

    public static GovernedActionPlan proposed(ActionPlan plan) {
        return new GovernedActionPlan(plan, ActionPlanStatus.PROPOSED);
    }

    public GovernedActionPlan transitionTo(ActionPlanStatus requested) {
        Objects.requireNonNull(requested, "requested must not be null");
        if (!status.canTransitionTo(requested)) {
            throw new InvalidActionPlanTransitionException(status, requested);
        }
        return new GovernedActionPlan(plan, requested);
    }

    public ActionPlanId id() {
        return plan.id();
    }

    public ActionPlanVersion version() {
        return plan.version();
    }

    public ActionPlanHash hash() {
        return plan.hash();
    }

    public ActionPlanFingerprint fingerprint() {
        return plan.fingerprint();
    }

    public ActionRisk risk() {
        return plan.risk();
    }
}
