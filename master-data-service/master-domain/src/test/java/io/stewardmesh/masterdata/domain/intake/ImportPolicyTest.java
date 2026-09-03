package io.stewardmesh.masterdata.domain.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class ImportPolicyTest {

    @Test
    void exposesSupplierWorkbookV1Limits() {
        var policy = ImportPolicy.supplierWorkbookV1();

        assertEquals(5_242_880L, policy.maxUploadBytes());
        assertEquals(1, policy.maxSheets());
        assertEquals(5_000, policy.maxDataRows());
        assertEquals(32, policy.maxColumns());
        assertEquals(4_096, policy.maxCellCharacters());
        assertEquals(50_000, policy.maxSharedStrings());
        assertEquals(1_000, policy.maxZipEntries());
        assertEquals(0.01, policy.minZipInflateRatio());
    }

    @Test
    void rejectsInvalidLimits() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImportPolicy(0, 1, 1, 1, 1, 1, 1, 0.01));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImportPolicy(1, 1, 1, 1, 1, 1, 1, Double.NaN));
        assertThrows(
                IllegalArgumentException.class,
                () -> new ImportPolicy(1, 1, 1, 1, 1, 1, 1, 1.01));
    }
}
