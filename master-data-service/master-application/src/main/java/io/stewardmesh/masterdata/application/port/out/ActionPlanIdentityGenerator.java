package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;

/** Server-owned identity source kept outside model-visible proposal arguments. */
@FunctionalInterface
public interface ActionPlanIdentityGenerator {

    ActionPlanId nextActionPlanId();
}
