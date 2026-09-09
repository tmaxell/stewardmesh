package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.application.intake.SupplierImportStatus;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import java.util.UUID;

public record SupplierImportStatusResponse(
        UUID importId, ImportStatus status, ImportCountersResponse counters, String failureCode) {

    static SupplierImportStatusResponse from(SupplierImportStatus status) {
        return new SupplierImportStatusResponse(
                status.importJobId().value(),
                status.status(),
                ImportCountersResponse.from(status.counters()),
                status.failureCode());
    }
}
