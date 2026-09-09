package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class JpaImportJobRepository implements ImportJobRepository {

    private final SpringDataImportJobRepository repository;

    JpaImportJobRepository(SpringDataImportJobRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ImportJob> findById(ImportJobId importJobId) {
        return repository.findById(importJobId.value()).map(ImportJobEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(ImportJob importJob) {
        var existing = repository.findById(importJob.id().value());
        var entity = existing.orElseGet(() -> ImportJobEntity.fromDomain(importJob));
        if (existing.isPresent()) {
            entity.apply(importJob);
        }
        repository.save(entity);
    }
}
