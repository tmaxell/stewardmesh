package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.intake.SupplierImportReport;
import io.stewardmesh.masterdata.application.intake.SupplierImportReportQuery;

/** Inbound boundary for reading bounded validation evidence. */
public interface GetSupplierImportReport
        extends UseCase<SupplierImportReportQuery, SupplierImportReport> {}
