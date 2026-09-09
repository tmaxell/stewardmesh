package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.intake.SupplierWorkbookParseRequest;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookParseResult;

/** Parses one bounded supplier workbook without exposing Apache POI to the application layer. */
@FunctionalInterface
public interface ParseSupplierWorkbook {

    SupplierWorkbookParseResult parse(SupplierWorkbookParseRequest request);
}
