package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Sealed plan context stored outside the immutable step projection. */
public record ActionPlanHeader(
        UUID planId,
        long planVersion,
        UUID importJobId,
        String planHash,
        String proposedBySubject,
        Instant proposedAt,
        ActionPlanStatus status) {

    public ActionPlanHeader {
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(planHash, "planHash must not be null");
        Objects.requireNonNull(proposedBySubject, "proposedBySubject must not be null");
        Objects.requireNonNull(proposedAt, "proposedAt must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
