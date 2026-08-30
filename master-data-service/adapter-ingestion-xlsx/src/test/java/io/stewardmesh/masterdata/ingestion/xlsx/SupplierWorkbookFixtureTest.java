package io.stewardmesh.masterdata.ingestion.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.Test;

class SupplierWorkbookFixtureTest {

    private static final Pattern CONTRACT_COLUMN_PATTERN = Pattern.compile(
            "\\\"position\\\"\\s*:\\s*\\d+\\s*,\\s*\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Test
    void validFixtureMatchesContractAndPreservesSupplierSiteDistinction() throws IOException {
        List<String> contractHeaders = contractHeaders();

        try (Workbook workbook = openFixture("supplier-workbook-v1-valid.xlsx")) {
            assertEquals(1, workbook.getNumberOfSheets());
            Sheet sheet = workbook.getSheet("suppliers");
            assertNotNull(sheet);
            assertEquals(contractHeaders, headerValues(sheet.getRow(0)));
            assertEquals(3, sheet.getLastRowNum());

            Row firstSite = sheet.getRow(2);
            Row secondSite = sheet.getRow(3);
            assertEquals(CellType.STRING, firstSite.getCell(3).getCellType());
            assertEquals(CellType.STRING, firstSite.getCell(4).getCellType());
            assertEquals(CellType.STRING, firstSite.getCell(7).getCellType());
            assertEquals(firstSite.getCell(3).getStringCellValue(), secondSite.getCell(3).getStringCellValue());
            assertEquals("990201001", firstSite.getCell(4).getStringCellValue());
            assertEquals("990201002", secondSite.getCell(4).getStringCellValue());
        }
    }

    @Test
    void mixedFixtureCarriesUnknownHeaderInvalidValuesAndFormulaEvidence() throws IOException {
        try (Workbook workbook = openFixture("supplier-workbook-v1-mixed-invalid.xlsx")) {
            Sheet sheet = workbook.getSheet("suppliers");
            List<String> headers = headerValues(sheet.getRow(0));
            assertEquals("unexpected_note", headers.getLast());

            Row invalidRow = sheet.getRow(2);
            assertEquals("", invalidRow.getCell(0).getStringCellValue());
            assertEquals(0.0, invalidRow.getCell(1).getNumericCellValue(), 0.0);
            assertEquals("123", invalidRow.getCell(3).getStringCellValue());
            assertEquals("UNKNOWN", invalidRow.getCell(13).getStringCellValue());

            assertEquals(CellType.FORMULA, sheet.getRow(3).getCell(2).getCellType());
            assertEquals("1+1", sheet.getRow(3).getCell(2).getCellFormula());
        }
    }

    private static Workbook openFixture(String name) throws IOException {
        try (InputStream input = SupplierWorkbookFixtureTest.class.getResourceAsStream("/fixtures/intake/" + name)) {
            assertNotNull(input, "missing fixture " + name);
            return WorkbookFactory.create(input);
        }
    }

    private static List<String> contractHeaders() throws IOException {
        try (InputStream input = SupplierWorkbookFixtureTest.class
                .getResourceAsStream("/contracts/intake/supplier-workbook-v1.json")) {
            assertNotNull(input, "missing supplier workbook contract");
            String contract = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            Matcher matcher = CONTRACT_COLUMN_PATTERN.matcher(contract);
            List<String> headers = new ArrayList<>();
            while (matcher.find()) {
                headers.add(matcher.group(1));
            }
            assertEquals(14, headers.size());
            return List.copyOf(headers);
        }
    }

    private static List<String> headerValues(Row row) {
        List<String> headers = new ArrayList<>();
        row.forEach(cell -> headers.add(cell.getStringCellValue()));
        return List.copyOf(headers);
    }
}
