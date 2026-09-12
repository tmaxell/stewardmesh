package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import java.util.Objects;

/** Model-visible execution arguments; identity and authorization remain server-owned. */
public record ExecuteActionPlanCommand(
        ActionPlanId planId,
        ActionPlanVersion expectedVersion,
        ActionPlanHash expectedHash,
        ExecutionRequestKey idempotencyKey,
        String reason) {

    public ExecuteActionPlanCommand {
        Objects.requireNonNull(planId, "plan id must not be null");
        Objects.requireNonNull(expectedVersion, "expected version must not be null");
        Objects.requireNonNull(expectedHash, "expected hash must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        reason = reason.strip();
        if (reason.isEmpty() || reason.length() > 512) {
            throw new IllegalArgumentException("execution reason must contain 1 to 512 characters");
        }
    }
}
