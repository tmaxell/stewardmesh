package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.util.Optional;

/** Persists immutable artifact metadata independently of object storage. */
public interface IntakeArtifactRepository {

    Optional<IntakeArtifact> findById(IntakeArtifactId artifactId);

    Optional<IntakeArtifact> findBySha256(String sha256);

    void save(IntakeArtifact artifact);
}
