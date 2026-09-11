package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.port.out.ActionPlanIdentityGenerator;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import java.util.UUID;

final class UuidActionPlanIdentityGenerator implements ActionPlanIdentityGenerator {

    @Override
    public ActionPlanId nextActionPlanId() {
        return new ActionPlanId(UUID.randomUUID());
    }
}
