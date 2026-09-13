package io.stewardmesh.masterdata.application.actionplan;

public final class ActionPlanNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ActionPlanNotFoundException() {
        super("action plan was not found");
    }
}
