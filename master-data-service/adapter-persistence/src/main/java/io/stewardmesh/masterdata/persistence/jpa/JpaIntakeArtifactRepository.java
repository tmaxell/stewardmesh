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
    public IntakeArtifact register(IntakeArtifact candidate) {
        repository.insertIfContentIsUnregistered(
                candidate.id().value(),
                candidate.sha256(),
                candidate.storageKey(),
                candidate.contentType(),
                candidate.sizeBytes(),
                candidate.createdAt());
        IntakeArtifact registered = repository
                .findBySha256(candidate.sha256())
                .map(IntakeArtifactEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException(
                        "registered artifact content disappeared before it could be read"));
        if (registered.id().equals(candidate.id()) && !registered.equals(candidate)) {
            throw new IllegalStateException("immutable artifact metadata cannot change");
        }
        return registered;
    }
}
