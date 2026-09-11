package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.List;
import java.util.Objects;

/** Model-visible proposal content; identity, time and actor context are deliberately absent. */
public record ProposeActionPlanCommand(ImportJobId importId, List<ActionPlanStep> steps) {

    public ProposeActionPlanCommand {
        Objects.requireNonNull(importId, "importId must not be null");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps must not be null"));
    }
}
