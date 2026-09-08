package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.util.Optional;
import org.springframework.transaction.annotation.Transactional;

public class JpaIntakeArtifactRepository implements IntakeArtifactRepository {

    private final SpringDataIntakeArtifactRepository repository;

    JpaIntakeArtifactRepository(SpringDataIntakeArtifactRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IntakeArtifact> findById(IntakeArtifactId artifactId) {
        return repository.findById(artifactId.value()).map(IntakeArtifactEntity::toDomain);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IntakeArtifact> findBySha256(String sha256) {
        return repository.findBySha256(sha256).map(IntakeArtifactEntity::toDomain);
    }

    @Override
    @Transactional
    public void save(IntakeArtifact artifact) {
        repository.findById(artifact.id().value()).ifPresentOrElse(existing -> {
            if (!existing.toDomain().equals(artifact)) {
                throw new IllegalStateException("immutable artifact metadata cannot change");
            }
        }, () -> repository.save(IntakeArtifactEntity.fromDomain(artifact)));
    }
}
