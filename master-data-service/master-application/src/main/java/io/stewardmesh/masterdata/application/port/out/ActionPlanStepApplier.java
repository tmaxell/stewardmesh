package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.actionplan.AppliedActionStep;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep;

/** Applies one already-authorized, already-simulated step using only local database resources. */
@FunctionalInterface
public interface ActionPlanStepApplier {

    AppliedActionStep apply(ActionPlanStep step);
}
