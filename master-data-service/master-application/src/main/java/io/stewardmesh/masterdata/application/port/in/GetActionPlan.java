package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;

/** READ boundary: returns a sealed plan with the governed status it currently holds. */
@FunctionalInterface
public interface GetActionPlan extends UseCase<ActionPlanId, GovernedActionPlan> {}
