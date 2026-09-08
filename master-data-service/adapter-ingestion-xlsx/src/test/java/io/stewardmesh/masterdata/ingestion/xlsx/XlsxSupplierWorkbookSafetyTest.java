package io.stewardmesh.masterdata.ingestion.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookParseRequest;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookParseResult;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

class XlsxSupplierWorkbookSafetyTest {

    private static final ImportPolicy DEFAULT_POLICY = ImportPolicy.supplierWorkbookV1();

    @Test
    void rejectsWorkbookWithMacroPartBeforeParsingRows() throws IOException {
        byte[] workbook = appendZipEntry(validWorkbook("suppliers", header -> {}), "xl/vbaProject.bin");

        var result = parse(DEFAULT_POLICY, workbook);

        assertFatal(result, ValidationCode.WORKBOOK_UNSAFE_CONTENT);
    }

    @Test
    void rejectsSuspiciousCompressionRatio() throws IOException {
        var strictCompressionPolicy = policy(
                DEFAULT_POLICY.maxDataRows(),
                DEFAULT_POLICY.maxColumns(),
                DEFAULT_POLICY.maxCellCharacters(),
                DEFAULT_POLICY.maxSharedStrings(),
                DEFAULT_POLICY.maxZipEntries(),
                0.99);

        var result = parse(strictCompressionPolicy, validWorkbook("suppliers", header -> {}));

        assertFatal(result, ValidationCode.WORKBOOK_UNSAFE_CONTENT);
    }

    @Test
    void enforcesRowColumnStringSharedStringAndZipEntryLimits() throws IOException {
        byte[] workbook = validWorkbook("suppliers", header -> {});
        List<ImportPolicy> policies = List.of(
                policy(1, 14, 4096, 50_000, 1_000, 0.01),
                policy(5_000, 13, 4096, 50_000, 1_000, 0.01),
                policy(5_000, 32, 8, 50_000, 1_000, 0.01),
                policy(5_000, 32, 4096, 1, 1_000, 0.01),
                policy(5_000, 32, 4096, 50_000, 1, 0.01));

        for (ImportPolicy policy : policies) {
            assertFatal(parse(policy, workbook), ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
        }
    }

    @Test
    void rejectsUnexpectedSheetAndReportsMissingContractSheet() throws IOException {
        var result = parse(DEFAULT_POLICY, validWorkbook("other", header -> {}));

        assertEquals(
                List.of(ValidationCode.WORKBOOK_UNEXPECTED_SHEET, ValidationCode.WORKBOOK_SHEET_MISSING),
                result.validationIssues().stream().map(issue -> issue.code()).toList());
        assertTrue(result.sourceRecords().isEmpty());
    }

    @Test
    void rejectsDuplicateAndMissingHeadersWithoutCreatingSourceAssertions() throws IOException {
        byte[] workbook = validWorkbook(
                "suppliers", header -> header.getCell(13).setCellValue("source_record_id"));

        var result = parse(DEFAULT_POLICY, workbook);

        assertEquals(
                List.of(ValidationCode.HEADER_DUPLICATE, ValidationCode.HEADER_MISSING),
                result.validationIssues().stream().map(issue -> issue.code()).toList());
        assertEquals("site_purpose", result.validationIssues().getLast().field());
        assertTrue(result.sourceRecords().isEmpty());
    }

    private static SupplierWorkbookParseResult parse(ImportPolicy policy, byte[] workbook) {
        var parser = new XlsxSupplierWorkbookParser(policy);
        var request = new SupplierWorkbookParseRequest(
                new ImportJobId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a")),
                new SourceSystemRef("synthetic-erp"),
                Instant.parse("2026-08-28T00:00:00Z"),
                new ByteArrayContent(workbook));
        return parser.parse(request);
    }

    private static void assertFatal(
            SupplierWorkbookParseResult result,
            ValidationCode code) {
        assertEquals(0, result.rowsRead());
        assertTrue(result.sourceRecords().isEmpty());
        assertEquals(List.of(code), result.validationIssues().stream().map(issue -> issue.code()).toList());
    }

    private static ImportPolicy policy(
            int maxRows,
            int maxColumns,
            int maxCellCharacters,
            int maxSharedStrings,
            int maxZipEntries,
            double minInflateRatio) {
        return new ImportPolicy(
                DEFAULT_POLICY.maxUploadBytes(),
                DEFAULT_POLICY.maxSheets(),
                maxRows,
                maxColumns,
                maxCellCharacters,
                maxSharedStrings,
                maxZipEntries,
                minInflateRatio);
    }

    private static byte[] validWorkbook(String sheetName, Consumer<XSSFRow> editHeader)
            throws IOException {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet(sheetName);
            var header = sheet.createRow(0);
            for (int index = 0; index < SupplierWorkbookV1Contract.COLUMNS.size(); index++) {
                header.createCell(index)
                        .setCellValue(SupplierWorkbookV1Contract.COLUMNS.get(index).name());
            }
            editHeader.accept(header);
            addValidRow(sheet.createRow(1), "SYN-SAFE-1");
            addValidRow(sheet.createRow(2), "SYN-SAFE-2");
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private static void addValidRow(XSSFRow row, String sourceRecordId) {
        List<String> values = List.of(
                sourceRecordId,
                "1",
                "Synthetic Supplier",
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
    }

    private static byte[] appendZipEntry(byte[] workbook, String entryName) throws IOException {
        try (var input = new ZipInputStream(new ByteArrayInputStream(workbook));
                var bytes = new ByteArrayOutputStream();
                var output = new ZipOutputStream(bytes)) {
            ZipEntry entry;
            while ((entry = input.getNextEntry()) != null) {
                output.putNextEntry(new ZipEntry(entry.getName()));
                input.transferTo(output);
                output.closeEntry();
            }
            output.putNextEntry(new ZipEntry(entryName));
            output.write(new byte[] {0, 1, 2, 3});
            output.closeEntry();
            output.finish();
            return bytes.toByteArray();
        }
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
