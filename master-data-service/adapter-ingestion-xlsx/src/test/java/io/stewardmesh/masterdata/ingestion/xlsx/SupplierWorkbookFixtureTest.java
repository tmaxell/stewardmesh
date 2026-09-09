package io.stewardmesh.masterdata.ingestion.xlsx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
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
    private static final Pattern VALIDATION_CODE_PATTERN =
            Pattern.compile("\\\"code\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

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

    @Test
    void domainSafetyPolicyMatchesThePublishedWorkbookContract() throws IOException {
        String contract = contractText();
        var policy = ImportPolicy.supplierWorkbookV1();

        assertEquals(policy.maxUploadBytes(), longProperty(contract, "maxUploadBytes"));
        assertEquals(policy.maxSheets(), longProperty(contract, "maxSheets"));
        assertEquals(policy.maxDataRows(), longProperty(contract, "maxDataRows"));
        assertEquals(policy.maxColumns(), longProperty(contract, "maxColumns"));
        assertEquals(policy.maxCellCharacters(), longProperty(contract, "maxCellCharacters"));
        assertEquals(policy.maxSharedStrings(), longProperty(contract, "maxSharedStrings"));
        assertEquals(policy.maxZipEntries(), longProperty(contract, "maxZipEntries"));
        assertEquals(policy.minZipInflateRatio(), doubleProperty(contract, "minZipInflateRatio"));
    }

    @Test
    void domainValidationTaxonomyMatchesThePublishedWorkbookContract() throws IOException {
        Matcher matcher = VALIDATION_CODE_PATTERN.matcher(contractText());
        List<String> contractCodes = new ArrayList<>();
        while (matcher.find()) {
            contractCodes.add(matcher.group(1));
        }
        List<String> domainCodes =
                Arrays.stream(ValidationCode.values()).map(Enum::name).toList();

        assertEquals(domainCodes, contractCodes);
    }

    private static Workbook openFixture(String name) throws IOException {
        try (InputStream input = SupplierWorkbookFixtureTest.class.getResourceAsStream("/fixtures/intake/" + name)) {
            assertNotNull(input, "missing fixture " + name);
            return WorkbookFactory.create(input);
        }
    }

    private static List<String> contractHeaders() throws IOException {
        String contract = contractText();
        Matcher matcher = CONTRACT_COLUMN_PATTERN.matcher(contract);
        List<String> headers = new ArrayList<>();
        while (matcher.find()) {
            headers.add(matcher.group(1));
        }
        assertEquals(14, headers.size());
        return List.copyOf(headers);
    }

    private static String contractText() throws IOException {
        try (InputStream input = SupplierWorkbookFixtureTest.class
                .getResourceAsStream("/contracts/intake/supplier-workbook-v1.json")) {
            assertNotNull(input, "missing supplier workbook contract");
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static long longProperty(String contract, String property) {
        return Long.parseLong(propertyValue(contract, property, "[0-9]+"));
    }

    private static double doubleProperty(String contract, String property) {
        return Double.parseDouble(propertyValue(contract, property, "[0-9]+(?:\\.[0-9]+)?"));
    }

    private static String propertyValue(String contract, String property, String numberPattern) {
        Pattern pattern = Pattern.compile(
                "\\\"" + Pattern.quote(property) + "\\\"\\s*:\\s*(" + numberPattern + ")");
        Matcher matcher = pattern.matcher(contract);
        if (!matcher.find()) {
            throw new IllegalArgumentException("missing numeric contract property " + property);
        }
        return matcher.group(1);
    }

    private static List<String> headerValues(Row row) {
        List<String> headers = new ArrayList<>();
        row.forEach(cell -> headers.add(cell.getStringCellValue()));
        return List.copyOf(headers);
    }
}
