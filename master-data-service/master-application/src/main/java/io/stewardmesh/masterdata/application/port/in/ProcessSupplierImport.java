package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.intake.ProcessSupplierImportResult;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;

/** Inbound boundary for deterministic parsing and validation. */
public interface ProcessSupplierImport extends UseCase<ImportJobId, ProcessSupplierImportResult> {}
