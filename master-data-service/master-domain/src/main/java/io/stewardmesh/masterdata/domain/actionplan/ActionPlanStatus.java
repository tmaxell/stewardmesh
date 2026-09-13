package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Objects;

/** Governed lifecycle of a sealed proposal; no transition outside this vocabulary is reachable. */
public enum ActionPlanStatus {
    PROPOSED,
    APPROVED,
    REJECTED,
    EXECUTING,
    EXECUTED,
    FAILED;

    public boolean isTerminal() {
        return this == REJECTED || this == EXECUTED || this == FAILED;
    }

    public boolean canTransitionTo(ActionPlanStatus requested) {
        Objects.requireNonNull(requested, "requested must not be null");
        return switch (this) {
            case PROPOSED -> requested == APPROVED || requested == REJECTED;
            case APPROVED -> requested == EXECUTING;
            case EXECUTING -> requested == EXECUTED || requested == FAILED;
            case REJECTED, EXECUTED, FAILED -> false;
        };
    }
}
