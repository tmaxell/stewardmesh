package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.actionplan.AuthenticatedProposalActor;
import io.stewardmesh.masterdata.application.actionplan.ProposeActionPlanCommand;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;

/** PROPOSE boundary: seals a reviewable plan but performs no authoritative business mutation. */
@FunctionalInterface
public interface ProposeActionPlan {

    ActionPlan propose(
            ProposeActionPlanCommand command, AuthenticatedProposalActor authenticatedActor);
}
