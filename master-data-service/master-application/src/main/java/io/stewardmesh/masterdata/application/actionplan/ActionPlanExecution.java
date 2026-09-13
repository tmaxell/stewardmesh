package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable receipt returned for both first execution and an idempotent replay. */
public record ActionPlanExecution(
        UUID executionId,
        ActionPlanId planId,
        ActionPlanVersion planVersion,
        ActionPlanHash planHash,
        ExecutionRequestKey requestKey,
        String executedBySubject,
        String reason,
        Instant executedAt,
        UUID correlationId,
        List<AppliedActionStep> effects) {

    public ActionPlanExecution {
        Objects.requireNonNull(executionId, "execution id must not be null");
        Objects.requireNonNull(planId, "plan id must not be null");
        Objects.requireNonNull(planVersion, "plan version must not be null");
        Objects.requireNonNull(planHash, "plan hash must not be null");
        Objects.requireNonNull(requestKey, "request key must not be null");
        executedBySubject = bounded(executedBySubject, "executed by subject", 128);
        reason = bounded(reason, "execution reason", 512);
        Objects.requireNonNull(executedAt, "executed at must not be null");
        Objects.requireNonNull(correlationId, "correlation id must not be null");
        effects = List.copyOf(Objects.requireNonNull(effects, "effects must not be null"));
        if (effects.isEmpty()) {
            throw new IllegalArgumentException("execution must contain at least one effect");
        }
    }

    private static String bounded(String value, String name, int maximum) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return normalized;
    }
}
