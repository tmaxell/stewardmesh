package io.stewardmesh.agent.eval;

import java.util.List;
import java.util.Objects;

/** Frozen expected outcome and resource bounds for an evaluation scenario. */
public record ExpectedEvalOutcome(
        String outcomeCode,
        String classification,
        boolean taskSuccess,
        boolean duplicate,
        boolean escalationRequired,
        boolean unsafeAction,
        boolean recoveryRequired,
        int maximumToolCalls,
        int maximumSideEffects,
        List<String> requiredEvidence) {

    public ExpectedEvalOutcome {
        outcomeCode = FrozenEvalDataset.requireText(outcomeCode, "outcomeCode");
        classification = FrozenEvalDataset.requireText(classification, "classification");
        if (maximumToolCalls < 0) {
            throw new IllegalArgumentException("maximumToolCalls must not be negative");
        }
        if (maximumSideEffects < 0) {
            throw new IllegalArgumentException("maximumSideEffects must not be negative");
        }
        requiredEvidence = List.copyOf(
                Objects.requireNonNull(requiredEvidence, "requiredEvidence must not be null"));
        if (requiredEvidence.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("requiredEvidence must contain non-blank values");
        }
        if (requiredEvidence.size() != requiredEvidence.stream().distinct().count()) {
            throw new IllegalArgumentException("requiredEvidence must not contain duplicates");
        }
    }
}
