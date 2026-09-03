package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.intake.SupplierImportReport;
import io.stewardmesh.masterdata.application.intake.SupplierImportReportQuery;

/** Reads deterministic validation evidence through a bounded query. */
@FunctionalInterface
public interface ValidationIssueReader {

    SupplierImportReport read(SupplierImportReportQuery query);
}
