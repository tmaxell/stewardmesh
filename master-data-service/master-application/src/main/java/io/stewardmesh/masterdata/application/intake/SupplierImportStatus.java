package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportCounters;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import java.util.Objects;
import java.util.Optional;

/** Framework-neutral status projection returned by read boundaries. */
public record SupplierImportStatus(
        ImportJobId importJobId, ImportStatus status, ImportCounters counters, String failureCode) {

    public SupplierImportStatus {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(counters, "counters must not be null");
        if ((status == ImportStatus.FAILED) != (failureCode != null)) {
            throw new IllegalArgumentException("failure code must be present only for failed imports");
        }
    }

    public Optional<String> failure() {
        return Optional.ofNullable(failureCode);
    }
}
