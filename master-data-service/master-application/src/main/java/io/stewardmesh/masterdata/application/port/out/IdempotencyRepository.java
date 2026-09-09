package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportRequestIdentity;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** Resolves repeated start requests by their origin-scoped idempotency identity. */
public interface IdempotencyRepository {

    Optional<IdempotencyRecord> find(ImportRequestIdentity requestIdentity);

    void save(IdempotencyRecord record);

    record IdempotencyRecord(
            ImportRequestIdentity requestIdentity,
            ImportJobId importJobId,
            String artifactSha256,
            Instant createdAt) {

        public IdempotencyRecord {
            Objects.requireNonNull(requestIdentity, "requestIdentity must not be null");
            Objects.requireNonNull(importJobId, "importJobId must not be null");
            Objects.requireNonNull(artifactSha256, "artifactSha256 must not be null");
            Objects.requireNonNull(createdAt, "createdAt must not be null");
        }
    }
}
