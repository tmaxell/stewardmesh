package io.stewardmesh.masterdata.application.audit;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable, non-sensitive execution audit record. */
public record AuditEvent(
        UUID auditId,
        ActionPlanId planId,
        ActionPlanVersion planVersion,
        ActionPlanHash planHash,
        String actorSubject,
        String action,
        String result,
        String reason,
        Instant occurredAt,
        UUID correlationId,
        List<UUID> affectedEntities) {

    public AuditEvent {
        Objects.requireNonNull(auditId, "audit id must not be null");
        Objects.requireNonNull(planId, "plan id must not be null");
        Objects.requireNonNull(planVersion, "plan version must not be null");
        Objects.requireNonNull(planHash, "plan hash must not be null");
        actorSubject = require(actorSubject, "actor subject", 128);
        action = require(action, "action", 64);
        result = require(result, "result", 32);
        reason = require(reason, "reason", 512);
        Objects.requireNonNull(occurredAt, "occurred at must not be null");
        Objects.requireNonNull(correlationId, "correlation id must not be null");
        affectedEntities = List.copyOf(
                Objects.requireNonNull(affectedEntities, "affected entities must not be null"));
    }

    private static String require(String value, String name, int maximum) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return normalized;
    }
}
