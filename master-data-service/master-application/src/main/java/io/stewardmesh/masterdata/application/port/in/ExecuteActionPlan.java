package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanExecution;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedExecutionActor;
import io.stewardmesh.masterdata.application.actionplan.ExecuteActionPlanCommand;

/** EXECUTE boundary: applies only an exact approved plan through a separately authorized actor. */
@FunctionalInterface
public interface ExecuteActionPlan {

    ActionPlanExecution execute(
            ExecuteActionPlanCommand command, AuthenticatedExecutionActor authenticatedActor);
}
