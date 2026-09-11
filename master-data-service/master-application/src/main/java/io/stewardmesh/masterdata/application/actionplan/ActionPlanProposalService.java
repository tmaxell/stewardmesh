package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.application.port.in.ProposeActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanIdentityGenerator;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import java.time.Clock;
import java.util.Objects;

/** Deterministically seals proposed content with server-owned identity, time and actor context. */
public final class ActionPlanProposalService implements ProposeActionPlan {

    private final ActionPlanIdentityGenerator identityGenerator;
    private final Clock clock;

    public ActionPlanProposalService(ActionPlanIdentityGenerator identityGenerator, Clock clock) {
        this.identityGenerator =
                Objects.requireNonNull(identityGenerator, "identityGenerator must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ActionPlan propose(
            ProposeActionPlanCommand command, AuthenticatedProposalActor authenticatedActor) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(authenticatedActor, "authenticatedActor must not be null");
        return ActionPlan.propose(
                identityGenerator.nextActionPlanId(),
                ActionPlanVersion.initial(),
                command.importId(),
                clock.instant(),
                authenticatedActor.subject(),
                command.steps());
    }
}
