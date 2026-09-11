package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.actionplan.AuthenticatedProposalActor;
import io.stewardmesh.masterdata.application.actionplan.ProposeActionPlanCommand;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;

/** PROPOSE boundary: seals and stores a reviewable plan but performs no master-data mutation. */
@FunctionalInterface
public interface ProposeActionPlan {

    GovernedActionPlan propose(
            ProposeActionPlanCommand command, AuthenticatedProposalActor authenticatedActor);
}
