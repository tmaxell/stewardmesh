package io.stewardmesh.masterdata.ingestion.xlsx;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookParseRequest;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookParseResult;
import io.stewardmesh.masterdata.application.port.out.ParseSupplierWorkbook;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import io.stewardmesh.masterdata.domain.intake.ValidationSeverity;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.poi.openxml4j.exceptions.OpenXML4JException;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.openxml4j.util.ZipSecureFile;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.util.CellReference;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.model.SharedStrings;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.XMLFilterImpl;

/** Streaming Apache POI parser for the published supplier workbook v1 contract. */
public final class XlsxSupplierWorkbookParser implements ParseSupplierWorkbook {

    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_EXPANDED_ENTRY_MULTIPLIER = 2;

    private final ImportPolicy policy;

    public XlsxSupplierWorkbookParser(ImportPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        configurePoiZipSecurity(policy);
    }

    @Override
    public SupplierWorkbookParseResult parse(SupplierWorkbookParseRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        Path staged = null;
        try {
            staged = Files.createTempFile("stewardmesh-parse-", ".xlsx");
            stageBounded(request.content(), staged);
            preflightPackage(staged);
            return parsePackage(staged, request);
        } catch (WorkbookIssueException exception) {
            return fatal(exception.code());
        } catch (IOException | OpenXML4JException | SAXException | ParserConfigurationException exception) {
            return fatal(ValidationCode.WORKBOOK_FORMAT_INVALID);
        } finally {
            deleteStagedFile(staged);
        }
    }

    private void stageBounded(IntakeContent content, Path staged) throws IOException {
        if (content.sizeBytes() <= 0 || content.sizeBytes() > policy.maxUploadBytes()) {
            throw new WorkbookIssueException(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
        }
        long size = 0;
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        try (InputStream input = content.openStream(); OutputStream output = Files.newOutputStream(staged)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                size = Math.addExact(size, read);
                if (size > policy.maxUploadBytes()) {
                    throw new WorkbookIssueException(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
                }
                output.write(buffer, 0, read);
            }
        }
        if (size != content.sizeBytes()) {
            throw new WorkbookIssueException(ValidationCode.WORKBOOK_FORMAT_INVALID);
        }
    }

    private void preflightPackage(Path staged) throws IOException {
        int entries = 0;
        try (ZipFile zip = new ZipFile(staged.toFile())) {
            var iterator = zip.entries().asIterator();
            while (iterator.hasNext()) {
                ZipEntry entry = iterator.next();
                entries++;
                if (entries > policy.maxZipEntries()) {
                    throw new WorkbookIssueException(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
                }
                if (isUnsafePart(entry.getName())) {
                    throw new WorkbookIssueException(ValidationCode.WORKBOOK_UNSAFE_CONTENT);
                }
                if (entry.getSize() > maxExpandedEntryBytes()) {
                    throw new WorkbookIssueException(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
                }
                if (entry.getSize() > 0 && entry.getCompressedSize() > 0) {
                    double ratio = (double) entry.getCompressedSize() / (double) entry.getSize();
                    if (ratio < policy.minZipInflateRatio()) {
                        throw new WorkbookIssueException(ValidationCode.WORKBOOK_UNSAFE_CONTENT);
                    }
                }
            }
        }
    }

    private SupplierWorkbookParseResult parsePackage(Path staged, SupplierWorkbookParseRequest request)
            throws IOException, OpenXML4JException, SAXException, ParserConfigurationException {
        try (OPCPackage opcPackage = OPCPackage.open(staged.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(opcPackage, true);
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            if (sharedStrings != null && sharedStrings.getUniqueCount() > policy.maxSharedStrings()) {
                throw new WorkbookIssueException(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
            }

            List<ValidationIssue> workbookIssues = new ArrayList<>();
            SheetSelection sheet = selectSupplierSheet(reader, workbookIssues);
            if (sheet == null || hasError(workbookIssues)) {
                closeSelection(sheet);
                return new SupplierWorkbookParseResult(0, List.of(), workbookIssues);
            }

            RowCollector collector = new RowCollector(request);
            try {
                parseSheet(reader, sharedStrings, sheet.input(), collector);
            } catch (HeaderIssueException exception) {
                List<ValidationIssue> issues = new ArrayList<>(workbookIssues);
                issues.addAll(collector.issues());
                return new SupplierWorkbookParseResult(0, List.of(), issues);
            }
            List<ValidationIssue> issues = new ArrayList<>(workbookIssues);
            issues.addAll(collector.issues());
            return new SupplierWorkbookParseResult(collector.rowsRead(), collector.records(), issues);
        }
    }

    private SheetSelection selectSupplierSheet(
            XSSFReader reader, List<ValidationIssue> workbookIssues)
            throws IOException, OpenXML4JException {
        XSSFReader.SheetIterator sheets = reader.getSheetIterator();
        SheetSelection selected = null;
        int sheetCount = 0;
        while (sheets.hasNext()) {
            InputStream input = sheets.next();
            sheetCount++;
            String name = sheets.getSheetName();
            if (sheetCount > policy.maxSheets()) {
                input.close();
                closeSelection(selected);
                workbookIssues.add(issue(ValidationCode.WORKBOOK_LIMIT_EXCEEDED, null, null));
                return null;
            }
            if (SupplierWorkbookV1Contract.SHEET_NAME.equals(name)) {
                selected = new SheetSelection(input);
            } else {
                input.close();
                workbookIssues.add(issue(ValidationCode.WORKBOOK_UNEXPECTED_SHEET, null, null));
            }
        }
        if (selected == null) {
            workbookIssues.add(issue(ValidationCode.WORKBOOK_SHEET_MISSING, null, null));
        }
        return selected;
    }

    private void parseSheet(
            XSSFReader reader,
            SharedStrings sharedStrings,
            InputStream sheetInput,
            RowCollector collector)
            throws IOException, SAXException, ParserConfigurationException, OpenXML4JException {
        try (sheetInput) {
            var handler = new XSSFSheetXMLHandler(
                    reader.getStylesTable(),
                    sharedStrings,
                    collector,
                    new DataFormatter(Locale.ROOT, false),
                    false);
            XMLReader xmlReader = XMLHelper.newXMLReader();
            FormulaDetectingFilter filter = new FormulaDetectingFilter(collector);
            filter.setParent(xmlReader);
            filter.setContentHandler(handler);
            filter.parse(new InputSource(sheetInput));
        }
    }

    private final class RowCollector implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final SupplierWorkbookParseRequest request;
        private final List<SourceRecord> records = new ArrayList<>();
        private final List<ValidationIssue> issues = new ArrayList<>();
        private final Map<Integer, String> headers = new HashMap<>();
        private final Map<Integer, String> cells = new HashMap<>();
        private final List<CellIssue> cellIssues = new ArrayList<>();
        private int currentRow;
        private int rowsRead;
        private boolean headersValid;

        private RowCollector(SupplierWorkbookParseRequest request) {
            this.request = request;
        }

        @Override
        public void startRow(int rowNumber) {
            currentRow = rowNumber + 1;
            cells.clear();
            cellIssues.clear();
        }

        @Override
        public void endRow(int rowNumber) {
            if (rowNumber == 0) {
                validateHeaders();
                if (!headersValid) {
                    throw new HeaderIssueException();
                }
                return;
            }
            if (cells.values().stream().allMatch(String::isEmpty) && cellIssues.isEmpty()) {
                return;
            }
            rowsRead++;
            if (rowsRead > policy.maxDataRows()) {
                throw new WorkbookIssueException(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
            }
            if (!headersValid) {
                return;
            }
            parseDataRow();
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = new CellReference(cellReference).getCol();
            if (column >= policy.maxColumns()) {
                throw new WorkbookIssueException(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
            }
            String value = formattedValue == null ? "" : formattedValue;
            if (value.length() > policy.maxCellCharacters()) {
                throw new WorkbookIssueException(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
            }
            cells.put(column, value);
        }

        private void formula(String cellReference) {
            int column = new CellReference(cellReference).getCol();
            cellIssues.add(new CellIssue(ValidationCode.FORMULA_CELL_NOT_ALLOWED, column));
        }

        private void validateHeaders() {
            Set<String> seen = new HashSet<>();
            cells.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                String header = entry.getValue();
                if (header.isBlank()) {
                    return;
                }
                if (!seen.add(header)) {
                    issues.add(issue(ValidationCode.HEADER_DUPLICATE, 1, null));
                } else if (!SupplierWorkbookV1Contract.BY_NAME.containsKey(header)) {
                    issues.add(issue(ValidationCode.HEADER_UNKNOWN, 1, null));
                } else {
                    headers.put(entry.getKey(), header);
                }
            });
            SupplierWorkbookV1Contract.COLUMNS.stream()
                    .map(SupplierWorkbookV1Contract.Column::name)
                    .filter(expected -> !seen.contains(expected))
                    .forEach(missing -> issues.add(issue(ValidationCode.HEADER_MISSING, 1, missing)));
            cellIssues.forEach(cellIssue -> issues.add(
                    issue(cellIssue.code(), currentRow, headers.get(cellIssue.column()))));
            headersValid = !hasError(issues);
        }

        private void parseDataRow() {
            Map<String, String> original = new LinkedHashMap<>();
            SupplierWorkbookV1Contract.COLUMNS.forEach(column -> original.put(column.name(), ""));
            cells.forEach((column, value) -> {
                String header = headers.get(column);
                if (header != null) {
                    original.put(header, value);
                }
            });

            List<ValidationIssue> rowIssues = new ArrayList<>();
            cellIssues.forEach(cellIssue -> rowIssues.add(issue(
                    cellIssue.code(), currentRow, headers.get(cellIssue.column()))));
            validateValues(original, rowIssues);
            issues.addAll(rowIssues);
            if (rowIssues.stream().anyMatch(value -> value.severity() == ValidationSeverity.ERROR)) {
                return;
            }

            long sourceVersion = Long.parseLong(original.get("source_version").trim());
            Map<String, String> canonical = canonicalValues(original);
            records.add(new SourceRecord(
                    new SourceRecordIdentity(
                            request.originSystem(), original.get("source_record_id").trim(), sourceVersion),
                    request.importJobId(),
                    request.ingestedAt(),
                    original,
                    canonical));
        }

        private void validateValues(
                Map<String, String> original, List<ValidationIssue> rowIssues) {
            for (SupplierWorkbookV1Contract.Column column : SupplierWorkbookV1Contract.COLUMNS) {
                String value = original.get(column.name());
                String checked = value.trim();
                if (checked.isEmpty()) {
                    if (column.valueRequired()) {
                        rowIssues.add(issue(
                                ValidationCode.REQUIRED_VALUE_MISSING, currentRow, column.name()));
                    }
                    continue;
                }
                if (column.maxCharacters() != null && value.length() > column.maxCharacters()) {
                    rowIssues.add(issue(ValidationCode.VALUE_TOO_LONG, currentRow, column.name()));
                }
                if (column.pattern() != null && !column.pattern().matcher(checked).matches()) {
                    rowIssues.add(issue(ValidationCode.VALUE_FORMAT_INVALID, currentRow, column.name()));
                }
                if (!column.allowedValues().isEmpty() && !column.allowedValues().contains(checked)) {
                    rowIssues.add(issue(ValidationCode.VALUE_NOT_ALLOWED, currentRow, column.name()));
                }
            }
            validateSourceVersion(original.get("source_version"), rowIssues);
            validateConditionalValues(original, rowIssues);
        }

        private void validateSourceVersion(String original, List<ValidationIssue> rowIssues) {
            String value = original.trim();
            if (value.isEmpty() || !value.matches("^[0-9]+$")) {
                return;
            }
            try {
                long parsed = Long.parseLong(value);
                if (parsed <= 0 || parsed > Integer.MAX_VALUE) {
                    rowIssues.add(issue(
                            ValidationCode.VALUE_FORMAT_INVALID, currentRow, "source_version"));
                }
            } catch (NumberFormatException exception) {
                rowIssues.add(issue(
                        ValidationCode.VALUE_FORMAT_INVALID, currentRow, "source_version"));
            }
        }

        private void validateConditionalValues(
                Map<String, String> original, List<ValidationIssue> rowIssues) {
            boolean hasSiteContext = present(original, "site_code")
                    || present(original, "procurement_bu_code")
                    || present(original, "site_purpose");
            if ("RU".equals(original.get("country_code").trim())
                    && hasSiteContext
                    && !present(original, "kpp")) {
                rowIssues.add(issue(ValidationCode.CONDITIONAL_VALUE_MISSING, currentRow, "kpp"));
            }
            if (present(original, "site_purpose") && !present(original, "procurement_bu_code")) {
                rowIssues.add(issue(
                        ValidationCode.CONDITIONAL_VALUE_MISSING,
                        currentRow,
                        "procurement_bu_code"));
            }
        }

        private List<SourceRecord> records() {
            return List.copyOf(records);
        }

        private List<ValidationIssue> issues() {
            return List.copyOf(issues);
        }

        private int rowsRead() {
            return rowsRead;
        }
    }

    private static Map<String, String> canonicalValues(Map<String, String> original) {
        Map<String, String> canonical = new LinkedHashMap<>();
        original.forEach((field, value) -> {
            String normalized = value.trim();
            if (!normalized.isEmpty()) {
                canonical.put(field, normalized);
            }
        });
        return canonical;
    }

    private static boolean present(Map<String, String> values, String field) {
        return !values.get(field).trim().isEmpty();
    }

    private static boolean hasError(List<ValidationIssue> issues) {
        return issues.stream().anyMatch(issue -> issue.severity() == ValidationSeverity.ERROR);
    }

    private static ValidationIssue issue(ValidationCode code, Integer row, String field) {
        return new ValidationIssue(code, row, field, Map.of());
    }

    private static SupplierWorkbookParseResult fatal(ValidationCode code) {
        return new SupplierWorkbookParseResult(0, List.of(), List.of(issue(code, null, null)));
    }

    private static boolean isUnsafePart(String name) {
        String normalized = name.toLowerCase(Locale.ROOT);
        return normalized.equals("xl/vbaproject.bin")
                || normalized.startsWith("xl/embeddings/")
                || normalized.startsWith("xl/activex/")
                || normalized.startsWith("xl/oleobjects/");
    }

    private long maxExpandedEntryBytes() {
        return Math.multiplyExact(policy.maxUploadBytes(), MAX_EXPANDED_ENTRY_MULTIPLIER);
    }

    private static void configurePoiZipSecurity(ImportPolicy policy) {
        synchronized (ZipSecureFile.class) {
            long maxText = Math.multiplyExact(
                    policy.maxUploadBytes(), MAX_EXPANDED_ENTRY_MULTIPLIER);
            ZipSecureFile.setMinInflateRatio(policy.minZipInflateRatio());
            ZipSecureFile.setMaxFileCount(policy.maxZipEntries());
            ZipSecureFile.setMaxEntrySize(maxText);
            ZipSecureFile.setMaxTextSize(maxText);
        }
    }

    private static void closeSelection(SheetSelection selection) throws IOException {
        if (selection != null) {
            selection.input().close();
        }
    }

    private static void deleteStagedFile(Path staged) {
        if (staged == null) {
            return;
        }
        try {
            Files.deleteIfExists(staged);
        } catch (IOException exception) {
            staged.toFile().deleteOnExit();
        }
    }

    private record SheetSelection(InputStream input) {}

    private record CellIssue(ValidationCode code, int column) {}

    private static final class FormulaDetectingFilter extends XMLFilterImpl {

        private final RowCollector collector;
        private String currentCellReference;

        private FormulaDetectingFilter(RowCollector collector) {
            this.collector = collector;
        }

        @Override
        public void startElement(
            String uri, String localName, String qualifiedName, Attributes attributes)
                throws SAXException {
            String element = localName.isEmpty()
                    ? qualifiedName.substring(qualifiedName.lastIndexOf(':') + 1)
                    : localName;
            if ("c".equals(element)) {
                currentCellReference = attributes.getValue("r");
            } else if ("f".equals(element) && currentCellReference != null) {
                collector.formula(currentCellReference);
            }
            super.startElement(uri, localName, qualifiedName, attributes);
        }
    }

    private static final class WorkbookIssueException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private final ValidationCode code;

        private WorkbookIssueException(ValidationCode code) {
            super(code.name());
            this.code = code;
        }

        private ValidationCode code() {
            return code;
        }
    }

    private static final class HeaderIssueException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private HeaderIssueException() {
            super("supplier workbook header is invalid");
        }
    }
}
