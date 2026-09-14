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
        // The stored row is the authority, not the candidate. Comparing the two for equality would
        // compare a creation instant that PostgreSQL keeps only to microsecond precision, so a
        // finer clock would make every registration look like a contradiction.
        return repository
                .findBySha256(candidate.sha256())
                .map(IntakeArtifactEntity::toDomain)
                .orElseThrow(() -> new IllegalStateException(
                        "registered artifact content disappeared before it could be read"));
    }
}
