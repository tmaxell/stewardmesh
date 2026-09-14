package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.util.Optional;

/** Persists immutable artifact metadata independently of object storage. */
public interface IntakeArtifactRepository {

    Optional<IntakeArtifact> findById(IntakeArtifactId artifactId);

    Optional<IntakeArtifact> findBySha256(String sha256);

    /**
     * Registers content-addressed metadata and returns the artifact that owns this content.
     *
     * <p>Two callers may upload identical bytes at the same time. Reading before writing would let
     * both miss and both insert, so registration has to be one atomic step and the caller has to
     * use the returned artifact rather than the candidate it offered.
     */
    IntakeArtifact register(IntakeArtifact candidate);
}
