package io.stewardmesh.masterdata.domain.actionplan;

/** Raised when a caller attempts to bypass the governed plan lifecycle. */
public final class InvalidActionPlanTransitionException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public InvalidActionPlanTransitionException(
            ActionPlanStatus current, ActionPlanStatus requested) {
        super("cannot transition action plan from " + current + " to " + requested);
    }
}
