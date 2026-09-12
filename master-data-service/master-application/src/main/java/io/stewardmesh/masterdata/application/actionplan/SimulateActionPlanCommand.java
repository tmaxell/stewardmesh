package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import java.util.Objects;

/**
 * Simulation is only meaningful for one exact sealed plan, so the caller states the version and
 * hash it believes it is reasoning about.
 */
public record SimulateActionPlanCommand(
        ActionPlanId planId, ActionPlanVersion expectedVersion, ActionPlanHash expectedHash) {

    public SimulateActionPlanCommand {
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(expectedVersion, "expectedVersion must not be null");
        Objects.requireNonNull(expectedHash, "expectedHash must not be null");
    }
}
