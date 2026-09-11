package io.stewardmesh.masterdata.domain.actionplan;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable proposal. Simulation, approval and execution must all reference the exact version and
 * hash exposed by this value.
 */
public record ActionPlan(
        ActionPlanId id,
        ActionPlanVersion version,
        ImportJobId importId,
        Instant createdAt,
        String proposedBySubject,
        List<ActionPlanStep> steps,
        ActionPlanHash hash) {

    private static final int MAX_STEPS = 50;
    private static final int MAX_SUBJECT_LENGTH = 128;

    public ActionPlan {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(importId, "importId must not be null");
        Objects.requireNonNull(createdAt, "createdAt must not be null");
        proposedBySubject = ActionPlanText.requireNonBlank(
                proposedBySubject, "proposed by subject", MAX_SUBJECT_LENGTH);
        steps = requireSteps(steps);
        Objects.requireNonNull(hash, "hash must not be null");

        ActionPlanHash expected = ActionPlanDigest.calculate(
                id, version, importId, createdAt, proposedBySubject, steps);
        if (!expected.equals(hash)) {
            throw new IllegalArgumentException("action plan hash does not match its immutable content");
        }
    }

    public static ActionPlan propose(
            ActionPlanId id,
            ActionPlanVersion version,
            ImportJobId importId,
            Instant createdAt,
            String proposedBySubject,
            List<ActionPlanStep> steps) {
        List<ActionPlanStep> immutableSteps = requireSteps(steps);
        ActionPlanHash hash = ActionPlanDigest.calculate(
                id, version, importId, createdAt, proposedBySubject, immutableSteps);
        return new ActionPlan(
                id, version, importId, createdAt, proposedBySubject, immutableSteps, hash);
    }

    public ActionRisk risk() {
        return steps.stream()
                .map(ActionPlanStep::risk)
                .max(Enum::compareTo)
                .orElseThrow();
    }

    private static List<ActionPlanStep> requireSteps(List<ActionPlanStep> steps) {
        Objects.requireNonNull(steps, "steps must not be null");
        if (steps.isEmpty() || steps.size() > MAX_STEPS) {
            throw new IllegalArgumentException("action plan step count must be between 1 and 50");
        }
        List<ActionPlanStep> immutable = steps.stream()
                .map(step -> Objects.requireNonNull(step, "action plan step must not be null"))
                .toList();
        for (int index = 0; index < immutable.size(); index++) {
            if (immutable.get(index).sequence() != index + 1) {
                throw new IllegalArgumentException(
                        "action plan steps must have contiguous sequence starting at 1");
            }
        }
        return immutable;
    }
}
