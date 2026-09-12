package io.stewardmesh.masterdata.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataActionPlanApprovalRepository
        extends JpaRepository<ActionPlanApprovalEntity, UUID> {

    Optional<ActionPlanApprovalEntity> findByDecidedBySubjectAndRequestKey(
            String decidedBySubject, String requestKey);
}
