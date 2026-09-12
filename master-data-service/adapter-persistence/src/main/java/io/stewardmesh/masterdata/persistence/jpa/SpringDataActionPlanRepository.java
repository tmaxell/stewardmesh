package io.stewardmesh.masterdata.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataActionPlanRepository extends JpaRepository<ActionPlanEntity, UUID> {

    Optional<ActionPlanEntity> findByContentFingerprintAndStatus(
            String contentFingerprint, String status);
}
