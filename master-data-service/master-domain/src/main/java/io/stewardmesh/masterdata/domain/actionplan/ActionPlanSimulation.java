package io.stewardmesh.masterdata.domain.actionplan;

import java.util.List;
import java.util.Objects;

/**
 * Read-only verdict for one exact sealed plan version. Simulation never mutates master data and is
 * only meaningful together with the plan version and hash it was calculated for.
 */
public record ActionPlanSimulation(
        ActionPlanId planId,
        ActionPlanVersion planVersion,
        ActionPlanHash planHash,
        SimulationOutcome outcome,
        List<SimulatedStep> steps) {

    public ActionPlanSimulation {
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(planVersion, "planVersion must not be null");
        Objects.requireNonNull(planHash, "planHash must not be null");
        Objects.requireNonNull(outcome, "outcome must not be null");
        steps = List.copyOf(Objects.requireNonNull(steps, "steps must not be null"));
        if (steps.isEmpty()) {
            throw new IllegalArgumentException("a simulated plan must have at least one step");
        }
        boolean executable = steps.stream().allMatch(SimulatedStep::isExecutable);
        if (executable != (outcome == SimulationOutcome.EXECUTABLE)) {
            throw new IllegalArgumentException("simulation outcome contradicts its step verdicts");
        }
    }

    public List<PreconditionCode> violations() {
        return steps.stream()
                .flatMap(step -> step.violations().stream())
                .distinct()
                .sorted()
                .toList();
    }
}
