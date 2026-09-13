package io.stewardmesh.masterdata.domain.actionplan;

/** Monotonic version to which simulation and approval are bound. */
public record ActionPlanVersion(long value) {

    public ActionPlanVersion {
        if (value <= 0) {
            throw new IllegalArgumentException("action plan version must be positive");
        }
    }

    public static ActionPlanVersion initial() {
        return new ActionPlanVersion(1);
    }
}
