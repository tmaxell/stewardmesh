package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.intake.SupplierImportStatus;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;

/** Inbound boundary for reading import progress without exposing persistence entities. */
public interface GetSupplierImportStatus extends UseCase<ImportJobId, SupplierImportStatus> {}
