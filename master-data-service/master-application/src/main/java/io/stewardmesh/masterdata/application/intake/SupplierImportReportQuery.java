package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.Objects;

/** Requests one bounded, zero-based page of validation evidence. */
public record SupplierImportReportQuery(ImportJobId importJobId, int page, int size) {

    public SupplierImportReportQuery {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        if (page < 0) {
            throw new IllegalArgumentException("page must not be negative");
        }
        if (size < 1 || size > 100) {
            throw new IllegalArgumentException("report page size must be within [1, 100]");
        }
    }
}
