package io.stewardmesh.masterdata.ingestion.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookColumnProfile;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookProfilingException;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.Test;

class XlsxSupplierWorkbookProfilerTest {

    private final XlsxSupplierWorkbookProfiler profiler =
            new XlsxSupplierWorkbookProfiler(ImportPolicy.supplierWorkbookV1());

    @Test
    void profilesCanonicalStructureAndAggregateCompletenessWithoutRowValues() throws IOException {
        var profile = profiler.profile(content(fixture("supplier-workbook-v1-valid.xlsx")));

        assertEquals("1.1.0", profile.contractVersion());
        assertEquals("suppliers", profile.sheetName());
        assertEquals(3, profile.dataRows());
        assertEquals(14, profile.columns().size());
        assertTrue(profile.columns().stream().allMatch(SupplierWorkbookColumnProfile::canonical));
        SupplierWorkbookColumnProfile kpp = column(profile.columns(), "kpp");
        assertEquals(2, kpp.nonBlankValues());
        assertEquals(1, kpp.blankValues());
        assertEquals(0, kpp.formulaCells());
        assertFalse(profile.toString().contains("Синтетик"));
    }

    @Test
    void identifiesUnknownColumnsAndCountsFormulaCellsWithoutEvaluation() throws IOException {
        var profile = profiler.profile(content(fixture("supplier-workbook-v1-mixed-invalid.xlsx")));

        assertEquals(3, profile.dataRows());
        assertEquals(1, profile.columns().stream().filter(column -> !column.canonical()).count());
        assertEquals(1, column(profile.columns(), "legal_name").formulaCells());
    }

    @Test
    void rejectsMalformedContentWithAStableCode() {
        var exception = assertThrows(
                SupplierWorkbookProfilingException.class,
                () -> profiler.profile(content("not-an-xlsx".getBytes(java.nio.charset.StandardCharsets.UTF_8))));

        assertEquals(ValidationCode.WORKBOOK_FORMAT_INVALID, exception.code());
    }

    private static SupplierWorkbookColumnProfile column(
            java.util.List<SupplierWorkbookColumnProfile> columns, String header) {
        return columns.stream().filter(column -> column.header().equals(header)).findFirst().orElseThrow();
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream input = XlsxSupplierWorkbookProfilerTest.class
                .getResourceAsStream("/fixtures/intake/" + name)) {
            assertNotNull(input, "missing fixture " + name);
            return input.readAllBytes();
        }
    }

    private static IntakeContent content(byte[] bytes) {
        return new IntakeContent() {
            @Override public String contentType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
            @Override public long sizeBytes() { return bytes.length; }
            @Override public InputStream openStream() { return new ByteArrayInputStream(bytes); }
        };
    }
}
