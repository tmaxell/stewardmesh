package io.stewardmesh.masterdata.application.actionplan;

public final class ActionPlanConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ActionPlanConflictException(String message) {
        super(message);
    }

    public ActionPlanConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
