package io.stewardmesh.masterdata.domain.actionplan;

import java.util.List;
import java.util.Objects;

/** Per-step simulation verdict; an executable step carries no violations. */
public record SimulatedStep(int sequence, ActionType type, List<PreconditionCode> violations) {

    public SimulatedStep {
        sequence = ActionPlanStepSupport.requireSequence(sequence);
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(violations, "violations must not be null");
        violations = violations.stream()
                .map(code -> Objects.requireNonNull(code, "violation must not be null"))
                .distinct()
                .sorted()
                .toList();
    }

    public boolean isExecutable() {
        return violations.isEmpty();
    }
}
