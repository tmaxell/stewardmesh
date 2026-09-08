package io.stewardmesh.masterdata.application.intake;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import org.junit.jupiter.api.Test;

class SupplierWorkbookParseResultTest {

    @Test
    void snapshotsParserCollectionsAtTheApplicationBoundary() {
        var records = new ArrayList<io.stewardmesh.masterdata.domain.intake.SourceRecord>();
        var issues = new ArrayList<io.stewardmesh.masterdata.domain.intake.ValidationIssue>();
        var result = new SupplierWorkbookParseResult(0, records, issues);

        assertThrows(UnsupportedOperationException.class, () -> result.sourceRecords().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.validationIssues().clear());
    }

    @Test
    void rejectsImpossibleRowCounts() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SupplierWorkbookParseResult(-1, java.util.List.of(), java.util.List.of()));
    }
}
