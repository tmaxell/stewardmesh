package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.application.port.in.GetActionPlan;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import java.util.Objects;

public final class ActionPlanReadService implements GetActionPlan {

    private final ActionPlanRepository plans;

    public ActionPlanReadService(ActionPlanRepository plans) {
        this.plans = Objects.requireNonNull(plans, "plans must not be null");
    }

    @Override
    public GovernedActionPlan execute(ActionPlanId id) {
        Objects.requireNonNull(id, "id must not be null");
        return plans.findById(id).orElseThrow(ActionPlanNotFoundException::new);
    }
}
