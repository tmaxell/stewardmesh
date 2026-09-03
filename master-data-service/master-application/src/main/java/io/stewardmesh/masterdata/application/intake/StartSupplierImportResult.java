package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import java.util.Objects;

/** Start result explicitly identifies an idempotent replay. */
public record StartSupplierImportResult(ImportJobId importJobId, ImportStatus status, boolean replayed) {

    public StartSupplierImportResult {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
