package io.stewardmesh.masterdata.persistence.jpa;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface SpringDataIntakeArtifactRepository extends JpaRepository<IntakeArtifactEntity, UUID> {

    Optional<IntakeArtifactEntity> findBySha256(String sha256);

    /**
     * Inserts content-addressed metadata unless this content is already registered. PostgreSQL
     * decides the race, so a concurrent duplicate becomes a no-op instead of aborting the
     * transaction with a constraint violation.
     *
     * <p>The conflict is deliberately not narrowed to one index. The storage key is derived from
     * the digest, so a duplicate violates both unique constraints at once, and naming only the
     * digest would let PostgreSQL raise on the storage key instead of skipping the row.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            value =
                    """
                    INSERT INTO intake_artifact
                        (id, sha256, storage_key, content_type, size_bytes, created_at)
                    VALUES (:id, :sha256, :storageKey, :contentType, :sizeBytes, :createdAt)
                    ON CONFLICT DO NOTHING
                    """,
            nativeQuery = true)
    int insertIfContentIsUnregistered(
            @Param("id") UUID id,
            @Param("sha256") String sha256,
            @Param("storageKey") String storageKey,
            @Param("contentType") String contentType,
            @Param("sizeBytes") long sizeBytes,
            @Param("createdAt") Instant createdAt);
}
