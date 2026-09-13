package io.stewardmesh.masterdata.application.actionplan;

/** An authorized step could not produce the exact version-matched master-data effect. */
public final class ActionPlanStepApplyException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ActionPlanStepApplyException(String message) {
        super(message);
    }
}
