package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanFingerprint;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import java.util.Optional;

/** Stores sealed plans and their governed status; sealed content is never rewritten. */
public interface ActionPlanRepository {

    Optional<GovernedActionPlan> findById(ActionPlanId id);

    /**
     * Finds the plan that still awaits a decision for exactly this content. Plans that were already
     * approved, rejected or executed never block a fresh proposal.
     */
    Optional<GovernedActionPlan> findProposedByFingerprint(ActionPlanFingerprint fingerprint);

    /** Inserts a new sealed plan, or advances the status of one already stored. */
    GovernedActionPlan save(GovernedActionPlan plan);
}
