package io.stewardmesh.masterdata.persistence.jpa;

import java.time.Instant;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataIdempotencyRepository
        extends JpaRepository<IdempotencyEntity, IdempotencyEntityId> {

    /**
     * Claims the request identity unless a concurrent caller already holds it. PostgreSQL decides
     * the race, so the loser learns it lost instead of aborting on a constraint violation.
     *
     * @return one when this caller claimed the identity, zero when another caller already had it
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO idempotency_record
                        (source_system, idempotency_key, import_job_id, artifact_sha256, created_at)
                    VALUES (:sourceSystem, :idempotencyKey, :importJobId, :artifactSha256, :createdAt)
                    ON CONFLICT (source_system, idempotency_key) DO NOTHING
                    """,
            nativeQuery = true)
    int claimIdentity(
            @Param("sourceSystem") String sourceSystem,
            @Param("idempotencyKey") String idempotencyKey,
            @Param("importJobId") UUID importJobId,
            @Param("artifactSha256") String artifactSha256,
            @Param("createdAt") Instant createdAt);
}
