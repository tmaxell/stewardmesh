package io.stewardmesh.masterdata.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataIntakeArtifactRepository extends JpaRepository<IntakeArtifactEntity, UUID> {

    Optional<IntakeArtifactEntity> findBySha256(String sha256);
}
