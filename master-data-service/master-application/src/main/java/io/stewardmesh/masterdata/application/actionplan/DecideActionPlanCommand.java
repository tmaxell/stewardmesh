package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalDecision;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalRequestKey;
import java.util.Objects;

/** Model-visible decision arguments; authenticated identity and authority are intentionally absent. */
public record DecideActionPlanCommand(
        ActionPlanId planId,
        ActionPlanVersion expectedVersion,
        ActionPlanHash expectedHash,
        ApprovalDecision decision,
        ApprovalRequestKey idempotencyKey,
        String reason) {

    public DecideActionPlanCommand {
        Objects.requireNonNull(planId, "plan id must not be null");
        Objects.requireNonNull(expectedVersion, "expected version must not be null");
        Objects.requireNonNull(expectedHash, "expected hash must not be null");
        Objects.requireNonNull(decision, "decision must not be null");
        Objects.requireNonNull(idempotencyKey, "idempotency key must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
    }
}
