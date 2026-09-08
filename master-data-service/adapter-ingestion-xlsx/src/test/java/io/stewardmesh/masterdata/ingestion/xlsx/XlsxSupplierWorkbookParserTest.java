package io.stewardmesh.masterdata.ingestion.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookParseRequest;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class XlsxSupplierWorkbookParserTest {

    private static final ImportJobId IMPORT_ID = new ImportJobId(
            UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a"));
    private static final SourceSystemRef ORIGIN = new SourceSystemRef("synthetic-erp");
    private static final Instant INGESTED_AT = Instant.parse("2026-08-28T00:00:00Z");

    private final XlsxSupplierWorkbookParser parser =
            new XlsxSupplierWorkbookParser(ImportPolicy.supplierWorkbookV1());

    @Test
    void parsesValidFixtureIntoImmutableSourceAssertions() throws IOException {
        var result = parser.parse(request(fixture("supplier-workbook-v1-valid.xlsx")));

        assertEquals(3, result.rowsRead());
        assertEquals(3, result.sourceRecords().size());
        assertTrue(result.validationIssues().isEmpty());
        var site = result.sourceRecords().get(1);
        assertEquals(ORIGIN, site.identity().originSystem());
        assertEquals("SYN-0002", site.identity().sourceRecordId());
        assertEquals(1, site.identity().sourceVersion());
        assertEquals("АО «Синтетик Бета»", site.originalValues().get("legal_name"));
        assertEquals("990201001", site.canonicalValues().get("kpp"));
        assertEquals(IMPORT_ID, site.importJobId());
        assertEquals(INGESTED_AT, site.ingestedAt());
    }

    @Test
    void reportsMixedFixtureDeterministicallyWithoutEvaluatingFormulaCells() throws IOException {
        var result = parser.parse(request(fixture("supplier-workbook-v1-mixed-invalid.xlsx")));

        assertEquals(3, result.rowsRead());
        assertEquals(1, result.sourceRecords().size());
        assertIterableEquals(
                List.of(
                        "HEADER_UNKNOWN:1:null",
                        "REQUIRED_VALUE_MISSING:3:source_record_id",
                        "REQUIRED_VALUE_MISSING:3:legal_name",
                        "VALUE_FORMAT_INVALID:3:inn",
                        "VALUE_NOT_ALLOWED:3:site_purpose",
                        "VALUE_FORMAT_INVALID:3:source_version",
                        "CONDITIONAL_VALUE_MISSING:3:kpp",
                        "CONDITIONAL_VALUE_MISSING:3:procurement_bu_code",
                        "FORMULA_CELL_NOT_ALLOWED:4:legal_name"),
                result.validationIssues().stream().map(XlsxSupplierWorkbookParserTest::signature).toList());
        assertTrue(result.validationIssues().stream()
                .allMatch(issue -> issue.parameters().isEmpty()));
    }

    @Test
    void preservesOriginalWhitespaceAndStoresCanonicalValueSeparately() throws IOException {
        byte[] workbook = workbookWithLegalName("  Synthetic Supplier  ");

        var result = parser.parse(request(workbook));

        assertTrue(result.validationIssues().isEmpty());
        assertEquals("  Synthetic Supplier  ", result.sourceRecords().getFirst()
                .originalValues()
                .get("legal_name"));
        assertEquals("Synthetic Supplier", result.sourceRecords().getFirst()
                .canonicalValues()
                .get("legal_name"));
    }

    @Test
    void returnsStableFormatIssueForMalformedInput() {
        var result = parser.parse(request("not-an-xlsx".getBytes(java.nio.charset.StandardCharsets.UTF_8)));

        assertEquals(0, result.rowsRead());
        assertTrue(result.sourceRecords().isEmpty());
        assertEquals("WORKBOOK_FORMAT_INVALID:null:null", signature(result.validationIssues().getFirst()));
    }

    private static SupplierWorkbookParseRequest request(byte[] bytes) {
        return new SupplierWorkbookParseRequest(
                IMPORT_ID, ORIGIN, INGESTED_AT, new ByteArrayContent(bytes));
    }

    private static byte[] fixture(String name) throws IOException {
        try (InputStream input = XlsxSupplierWorkbookParserTest.class
                .getResourceAsStream("/fixtures/intake/" + name)) {
            assertNotNull(input, "missing fixture " + name);
            return input.readAllBytes();
        }
    }

    private static byte[] workbookWithLegalName(String legalName) throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("suppliers");
            var header = sheet.createRow(0);
            for (int index = 0; index < SupplierWorkbookV1Contract.COLUMNS.size(); index++) {
                header.createCell(index)
                        .setCellValue(SupplierWorkbookV1Contract.COLUMNS.get(index).name());
            }
            var row = sheet.createRow(1);
            List<String> values = List.of(
                    "SYN-TEST-1",
                    "1",
                    legalName,
                    "9901000002",
                    "",
                    "",
                    "RU",
                    "",
                    "Synthetic Region",
                    "Synthetic City",
                    "Synthetic Address",
                    "",
                    "",
                    "");
            for (int index = 0; index < values.size(); index++) {
                row.createCell(index).setCellValue(values.get(index));
            }
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static String signature(ValidationIssue issue) {
        return issue.code() + ":" + issue.rowNumber() + ":" + issue.field();
    }

    private record ByteArrayContent(byte[] bytes) implements IntakeContent {

        @Override
        public String contentType() {
            return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
        }

        @Override
        public long sizeBytes() {
            return bytes.length;
        }

        @Override
        public InputStream openStream() {
            return new ByteArrayInputStream(bytes);
        }
    }
}
