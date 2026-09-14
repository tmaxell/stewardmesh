package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.intake.ConcurrentImportRegistrationException;
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
        var identity = record.requestIdentity();
        int claimed = repository.claimIdentity(
                identity.sourceSystem().value(),
                identity.idempotencyKey().value(),
                record.importJobId().value(),
                record.artifactSha256(),
                record.createdAt());
        if (claimed == 0) {
            throw new ConcurrentImportRegistrationException();
        }
    }

    private static IdempotencyEntityId toId(ImportRequestIdentity identity) {
        return new IdempotencyEntityId(
                identity.sourceSystem().value(), identity.idempotencyKey().value());
    }
}
