package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.application.port.in.ProposeActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import java.time.Clock;
import java.util.Objects;

/**
 * Deterministically seals proposed content with server-owned identity, time and actor context, then
 * stores it exactly once. Re-proposing content that still awaits a decision returns the stored plan
 * instead of creating a second governed mutation.
 */
public final class ActionPlanProposalService implements ProposeActionPlan {

    private final ActionPlanRepository plans;
    private final ActionPlanIdentityGenerator identityGenerator;
    private final ApplicationTransaction transaction;
    private final Clock clock;

    public ActionPlanProposalService(
            ActionPlanRepository plans,
            ActionPlanIdentityGenerator identityGenerator,
            ApplicationTransaction transaction,
            Clock clock) {
        this.plans = Objects.requireNonNull(plans, "plans must not be null");
        this.identityGenerator =
                Objects.requireNonNull(identityGenerator, "identityGenerator must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public GovernedActionPlan propose(
            ProposeActionPlanCommand command, AuthenticatedProposalActor authenticatedActor) {
        Objects.requireNonNull(command, "command must not be null");
        Objects.requireNonNull(authenticatedActor, "authenticatedActor must not be null");
        ActionPlan candidate = seal(command, authenticatedActor);
        return transaction.execute(() -> store(candidate));
    }

    private ActionPlan seal(
            ProposeActionPlanCommand command, AuthenticatedProposalActor authenticatedActor) {
        return ActionPlan.propose(
                identityGenerator.nextActionPlanId(),
                ActionPlanVersion.initial(),
                command.importId(),
                clock.instant(),
                authenticatedActor.subject(),
                command.steps());
    }

    private GovernedActionPlan store(ActionPlan candidate) {
        return plans.findProposedByFingerprint(candidate.fingerprint())
                .orElseGet(() -> plans.save(GovernedActionPlan.proposed(candidate)));
    }
}
