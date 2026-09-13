package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.actionplan.AuthenticatedApprovalActor;
import io.stewardmesh.masterdata.application.actionplan.DecideActionPlanCommand;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;

/** Records a separately authorized human decision over an exact immutable plan. */
@FunctionalInterface
public interface DecideActionPlan {

    ActionPlanApproval decide(
            DecideActionPlanCommand command, AuthenticatedApprovalActor authenticatedActor);
}
