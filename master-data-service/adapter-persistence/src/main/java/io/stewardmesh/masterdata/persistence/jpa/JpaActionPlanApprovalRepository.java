package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.actionplan.ApprovalConflictException;
import io.stewardmesh.masterdata.application.port.out.ActionPlanApprovalRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalDecision;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalRequestKey;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;

/** JPA aggregate adapter for immutable action-plan approvals. */
public final class JpaActionPlanApprovalRepository implements ActionPlanApprovalRepository {

    private final SpringDataActionPlanApprovalRepository repository;

    JpaActionPlanApprovalRepository(SpringDataActionPlanApprovalRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Optional<ActionPlanApproval> findByPlanId(ActionPlanId planId) {
        Objects.requireNonNull(planId, "plan id must not be null");
        return repository.findById(planId.value()).map(JpaActionPlanApprovalRepository::toDomain);
    }

    @Override
    public Optional<ActionPlanApproval> findBySubjectAndRequestKey(
            String subject, ApprovalRequestKey requestKey) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(requestKey, "request key must not be null");
        return repository.findByDecidedBySubjectAndRequestKey(subject, requestKey.value())
                .map(JpaActionPlanApprovalRepository::toDomain);
    }

    @Override
    public ActionPlanApproval save(ActionPlanApproval approval) {
        Objects.requireNonNull(approval, "approval must not be null");
        if (repository.existsById(approval.planId().value())
                || repository.findByDecidedBySubjectAndRequestKey(
                                approval.decidedBySubject(), approval.requestKey().value())
                        .isPresent()) {
            throw new ApprovalConflictException(
                    "action plan or idempotency key already has a decision");
        }
        try {
            return toDomain(repository.saveAndFlush(new ActionPlanApprovalEntity(approval)));
        } catch (DataIntegrityViolationException failure) {
            throw new ApprovalConflictException(
                    "action plan or idempotency key already has a decision", failure);
        }
    }

    private static ActionPlanApproval toDomain(ActionPlanApprovalEntity entity) {
        return new ActionPlanApproval(
                new ActionPlanId(entity.planId()),
                new ActionPlanVersion(entity.planVersion()),
                new ActionPlanHash(entity.planHash()),
                ApprovalDecision.valueOf(entity.decision()),
                new ApprovalRequestKey(entity.requestKey()),
                entity.decidedBySubject(),
                entity.decidedAt(),
                entity.reason());
    }
}
