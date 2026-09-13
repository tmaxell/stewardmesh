package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;

/** Immutable JPA representation of a separately authorized plan decision. */
@Entity
@Table(
        name = "action_plan_approval",
        uniqueConstraints = @UniqueConstraint(
                columnNames = {"decided_by_subject", "request_key"}))
class ActionPlanApprovalEntity {

    @Id
    @Column(name = "plan_id", nullable = false, updatable = false)
    private UUID planId;

    @Column(name = "plan_version", nullable = false, updatable = false)
    private long planVersion;

    @Column(name = "plan_hash", nullable = false, updatable = false, length = 64)
    private String planHash;

    @Column(name = "decision", nullable = false, updatable = false, length = 8)
    private String decision;

    @Column(name = "request_key", nullable = false, updatable = false, length = 128)
    private String requestKey;

    @Column(name = "decided_by_subject", nullable = false, updatable = false, length = 128)
    private String decidedBySubject;

    @Column(name = "decided_at", nullable = false, updatable = false)
    private Instant decidedAt;

    @Column(name = "reason", nullable = false, updatable = false, length = 512)
    private String reason;

    protected ActionPlanApprovalEntity() {}

    ActionPlanApprovalEntity(ActionPlanApproval approval) {
        planId = approval.planId().value();
        planVersion = approval.planVersion().value();
        planHash = approval.planHash().value();
        decision = approval.decision().name();
        requestKey = approval.requestKey().value();
        decidedBySubject = approval.decidedBySubject();
        decidedAt = approval.decidedAt();
        reason = approval.reason();
    }

    UUID planId() {
        return planId;
    }

    long planVersion() {
        return planVersion;
    }

    String planHash() {
        return planHash;
    }

    String decision() {
        return decision;
    }

    String requestKey() {
        return requestKey;
    }

    String decidedBySubject() {
        return decidedBySubject;
    }

    Instant decidedAt() {
        return decidedAt;
    }

    String reason() {
        return reason;
    }
}
