package io.stewardmesh.masterdata.application.port.in;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.intake.SupplierImportReportQuery;
import io.stewardmesh.masterdata.application.intake.SupplierImportStatus;
import io.stewardmesh.masterdata.domain.intake.ImportCounters;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UseCaseBoundaryTest {

    @Test
    void coordinatesIntakeDomainValuesWithoutAFramework() {
        var importId = new ImportJobId(
                UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a"));
        GetSupplierImportStatus statusUseCase = ignored -> new SupplierImportStatus(
                importId, ImportStatus.RECEIVED, ImportCounters.EMPTY, null);

        assertEquals(importId, statusUseCase.execute(importId).importJobId());
        assertEquals(ImportStatus.RECEIVED, statusUseCase.execute(importId).status());
    }

    @Test
    void boundsValidationReportQueriesAtTheApplicationBoundary() {
        var importId = new ImportJobId(
                UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a"));

        assertEquals(100, new SupplierImportReportQuery(importId, 0, 100).size());
        assertThrows(
                IllegalArgumentException.class,
                () -> new SupplierImportReportQuery(importId, 0, 101));
    }
}
