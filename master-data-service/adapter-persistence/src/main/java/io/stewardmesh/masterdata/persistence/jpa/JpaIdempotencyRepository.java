package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.intake.IdempotencyConflictException;
import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository;
import io.stewardmesh.masterdata.domain.intake.ImportRequestIdentity;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class JpaIdempotencyRepository implements IdempotencyRepository {

    private final SpringDataIdempotencyRepository repository;

    JpaIdempotencyRepository(SpringDataIdempotencyRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IdempotencyRecord> find(ImportRequestIdentity requestIdentity) {
        return repository
                .findById(toId(requestIdentity))
                .map(IdempotencyEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(IdempotencyRecord record) {
        repository.findById(toId(record.requestIdentity())).ifPresentOrElse(existing -> {
            if (!existing.toDomain().equals(record)) {
                throw new IdempotencyConflictException();
            }
        }, () -> repository.save(IdempotencyEntity.fromDomain(record)));
    }

    private static IdempotencyEntityId toId(ImportRequestIdentity identity) {
        return new IdempotencyEntityId(
                identity.sourceSystem().value(), identity.idempotencyKey().value());
    }
}
