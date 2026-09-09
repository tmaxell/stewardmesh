package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.intake.StartSupplierImportCommand;
import io.stewardmesh.masterdata.application.intake.StartSupplierImportResult;

/** Inbound boundary for accepting a supplier workbook exactly once per request identity. */
public interface StartSupplierImport
        extends UseCase<StartSupplierImportCommand, StartSupplierImportResult> {}
