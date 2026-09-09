package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.application.port.in.GetSupplierImportStatus;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.Objects;

/** Reads a transport-neutral import status projection. */
public final class GetSupplierImportStatusService implements GetSupplierImportStatus {

    private final ImportJobRepository importJobRepository;

    public GetSupplierImportStatusService(ImportJobRepository importJobRepository) {
        this.importJobRepository =
                Objects.requireNonNull(importJobRepository, "importJobRepository must not be null");
    }

    @Override
    public SupplierImportStatus execute(ImportJobId importJobId) {
        ImportJob job = importJobRepository
                .findById(Objects.requireNonNull(importJobId, "importJobId must not be null"))
                .orElseThrow(() -> new SupplierImportNotFoundException(importJobId));
        return new SupplierImportStatus(
                job.id(), job.status(), job.counters(), job.failureCode().orElse(null));
    }
}
