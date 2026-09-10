package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import java.util.Objects;

public record RouteSupplierImportMatchesResult(ImportJobId importJobId, ImportStatus status) {

    public RouteSupplierImportMatchesResult {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(status, "status must not be null");
    }
}
