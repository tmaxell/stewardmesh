package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.InvalidActionPlanTransitionException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** Mutable governed status of a sealed plan; the plan content itself is an immutable projection. */
@Entity
@Table(name = "action_plan")
class ActionPlanEntity {

    @Id
    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "plan_version", nullable = false, updatable = false)
    private long planVersion;

    @Column(name = "import_job_id", nullable = false, updatable = false)
    private UUID importJobId;

    @Column(name = "content_fingerprint", nullable = false, updatable = false, length = 64)
    private String contentFingerprint;

    @Column(name = "plan_hash", nullable = false, updatable = false, length = 64)
    private String planHash;

    @Column(name = "proposed_by_subject", nullable = false, updatable = false, length = 128)
    private String proposedBySubject;

    @Column(name = "proposed_at", nullable = false, updatable = false)
    private Instant proposedAt;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "risk", nullable = false, updatable = false, length = 8)
    private String risk;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected ActionPlanEntity() {}

    ActionPlanEntity(GovernedActionPlan governed) {
        planId = governed.id().value();
        planVersion = governed.version().value();
        importJobId = governed.plan().importId().value();
        contentFingerprint = governed.fingerprint().value();
        planHash = governed.hash().value();
        proposedBySubject = governed.plan().proposedBySubject();
        proposedAt = governed.plan().createdAt();
        status = governed.status().name();
        risk = governed.risk().name();
    }

    void advanceTo(ActionPlanStatus requested) {
        ActionPlanStatus current = status();
        if (current == requested) {
            return;
        }
        if (!current.canTransitionTo(requested)) {
            throw new InvalidActionPlanTransitionException(current, requested);
        }
        status = requested.name();
    }

    UUID planId() {
        return planId;
    }

    long planVersion() {
        return planVersion;
    }

    UUID importJobId() {
        return importJobId;
    }

    String planHash() {
        return planHash;
    }

    String proposedBySubject() {
        return proposedBySubject;
    }

    Instant proposedAt() {
        return proposedAt;
    }

    ActionPlanStatus status() {
        return ActionPlanStatus.valueOf(status);
    }
}
