package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** JPA-owned governed status and optimistic lock; steps remain immutable JDBC projections. */
public class JpaActionPlanHeaderStore {

    private final SpringDataActionPlanRepository repository;

    JpaActionPlanHeaderStore(SpringDataActionPlanRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public Optional<ActionPlanHeader> findById(UUID planId) {
        Objects.requireNonNull(planId, "planId must not be null");
        return repository.findById(planId).map(JpaActionPlanHeaderStore::toHeader);
    }

    public Optional<ActionPlanHeader> findUndecided(String contentFingerprint) {
        Objects.requireNonNull(contentFingerprint, "contentFingerprint must not be null");
        return repository
                .findByContentFingerprintAndStatus(
                        contentFingerprint, ActionPlanStatus.PROPOSED.name())
                .map(JpaActionPlanHeaderStore::toHeader);
    }

    public void insert(GovernedActionPlan governed) {
        Objects.requireNonNull(governed, "governed must not be null");
        repository.saveAndFlush(new ActionPlanEntity(governed));
    }

    /** Advances the governed status of a stored plan, or reports that it is not stored yet. */
    public boolean advance(UUID planId, ActionPlanStatus requested) {
        Objects.requireNonNull(planId, "planId must not be null");
        Objects.requireNonNull(requested, "requested must not be null");
        var entity = repository.findById(planId);
        entity.ifPresent(stored -> {
            stored.advanceTo(requested);
            repository.saveAndFlush(stored);
        });
        return entity.isPresent();
    }

    private static ActionPlanHeader toHeader(ActionPlanEntity entity) {
        return new ActionPlanHeader(
                entity.planId(),
                entity.planVersion(),
                entity.importJobId(),
                entity.planHash(),
                entity.proposedBySubject(),
                entity.proposedAt(),
                entity.status());
    }
}
