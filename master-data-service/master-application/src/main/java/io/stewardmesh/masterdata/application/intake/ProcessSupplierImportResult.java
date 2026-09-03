package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportCounters;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import java.util.Objects;

/** Outcome of deterministic parsing and validation orchestration. */
public record ProcessSupplierImportResult(
        ImportJobId importJobId, ImportStatus status, ImportCounters counters) {

    public ProcessSupplierImportResult {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Objects.requireNonNull(counters, "counters must not be null");
    }
}
