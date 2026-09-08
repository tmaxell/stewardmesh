package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.util.List;
import java.util.Objects;

/** Deterministic parser output; only rows without errors become source records. */
public record SupplierWorkbookParseResult(
        int rowsRead, List<SourceRecord> sourceRecords, List<ValidationIssue> validationIssues) {

    public SupplierWorkbookParseResult {
        if (rowsRead < 0) {
            throw new IllegalArgumentException("rowsRead must not be negative");
        }
        sourceRecords = List.copyOf(
                Objects.requireNonNull(sourceRecords, "sourceRecords must not be null"));
        validationIssues = List.copyOf(
                Objects.requireNonNull(validationIssues, "validationIssues must not be null"));
        if (sourceRecords.size() > rowsRead) {
            throw new IllegalArgumentException("source record count must not exceed rows read");
        }
    }
}
