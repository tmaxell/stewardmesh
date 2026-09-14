package io.stewardmesh.masterdata.application.intake;

import java.util.List;
import java.util.Objects;

/** Deterministic, bounded workbook profile containing no source row values. */
public record SupplierWorkbookProfile(
        String contractVersion,
        String sheetName,
        int dataRows,
        List<SupplierWorkbookColumnProfile> columns) {

    public SupplierWorkbookProfile {
        Objects.requireNonNull(contractVersion, "contractVersion must not be null");
        Objects.requireNonNull(sheetName, "sheetName must not be null");
        if (contractVersion.isBlank() || sheetName.isBlank()) {
            throw new IllegalArgumentException("profile identifiers must not be blank");
        }
        if (dataRows < 0) {
            throw new IllegalArgumentException("dataRows must not be negative");
        }
        columns = List.copyOf(Objects.requireNonNull(columns, "columns must not be null"));
        if (columns.stream().anyMatch(column -> column.nonBlankValues() + column.blankValues() != dataRows)) {
            throw new IllegalArgumentException("every column must account for each profiled row");
        }
    }
}
