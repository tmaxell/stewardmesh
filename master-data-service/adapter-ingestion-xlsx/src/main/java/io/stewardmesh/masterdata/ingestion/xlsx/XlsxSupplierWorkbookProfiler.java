package io.stewardmesh.masterdata.ingestion.xlsx;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookColumnProfile;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookProfile;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookProfilingException;
import io.stewardmesh.masterdata.application.port.out.ProfileSupplierWorkbook;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

/** Streaming, value-free profiler guarded by the same limits as supplier workbook ingestion. */
public final class XlsxSupplierWorkbookProfiler implements ProfileSupplierWorkbook {

    private static final String CONTRACT_VERSION = "1.1.0";
    private static final int COPY_BUFFER_BYTES = 64 * 1024;
    private static final int MAX_EXPANDED_ENTRY_MULTIPLIER = 2;

    private final ImportPolicy policy;

    public XlsxSupplierWorkbookProfiler(ImportPolicy policy) {
        this.policy = Objects.requireNonNull(policy, "policy must not be null");
        configurePoiZipSecurity(policy);
    }

    @Override
    public SupplierWorkbookProfile profile(IntakeContent content) {
        Objects.requireNonNull(content, "content must not be null");
        Path staged = null;
        try {
            staged = Files.createTempFile("stewardmesh-profile-", ".xlsx");
            stageBounded(content, staged);
            preflightPackage(staged);
            return profilePackage(staged);
        } catch (SupplierWorkbookProfilingException exception) {
            throw exception;
        } catch (IOException | OpenXML4JException | SAXException | ParserConfigurationException exception) {
            throw new SupplierWorkbookProfilingException(ValidationCode.WORKBOOK_FORMAT_INVALID, exception);
        } finally {
            deleteStagedFile(staged);
        }
    }

    private void stageBounded(IntakeContent content, Path staged) throws IOException {
        if (content.sizeBytes() <= 0 || content.sizeBytes() > policy.maxUploadBytes()) {
            fail(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
        }
        long size = 0;
        byte[] buffer = new byte[COPY_BUFFER_BYTES];
        try (InputStream input = content.openStream(); OutputStream output = Files.newOutputStream(staged)) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                size = Math.addExact(size, read);
                if (size > policy.maxUploadBytes()) {
                    fail(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
                }
                output.write(buffer, 0, read);
            }
        }
        if (size != content.sizeBytes()) {
            fail(ValidationCode.WORKBOOK_FORMAT_INVALID);
        }
    }

    private void preflightPackage(Path staged) throws IOException {
        int entries = 0;
        try (ZipFile zip = new ZipFile(staged.toFile())) {
            var iterator = zip.entries().asIterator();
            while (iterator.hasNext()) {
                ZipEntry entry = iterator.next();
                entries++;
                if (entries > policy.maxZipEntries() || entry.getSize() > maxExpandedEntryBytes()) {
                    fail(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
                }
                if (isUnsafePart(entry.getName())) {
                    fail(ValidationCode.WORKBOOK_UNSAFE_CONTENT);
                }
                if (entry.getSize() > 0 && entry.getCompressedSize() > 0) {
                    double ratio = (double) entry.getCompressedSize() / (double) entry.getSize();
                    if (ratio < policy.minZipInflateRatio()) {
                        fail(ValidationCode.WORKBOOK_UNSAFE_CONTENT);
                    }
                }
            }
        }
    }

    private SupplierWorkbookProfile profilePackage(Path staged)
            throws IOException, OpenXML4JException, SAXException, ParserConfigurationException {
        try (OPCPackage opcPackage = OPCPackage.open(staged.toFile(), PackageAccess.READ)) {
            XSSFReader reader = new XSSFReader(opcPackage, true);
            SharedStrings sharedStrings = reader.getSharedStringsTable();
            if (sharedStrings != null && sharedStrings.getUniqueCount() > policy.maxSharedStrings()) {
                fail(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
            }
            try (InputStream sheet = selectSupplierSheet(reader)) {
                ProfileCollector collector = new ProfileCollector();
                var handler = new XSSFSheetXMLHandler(
                        reader.getStylesTable(), sharedStrings, collector,
                        new DataFormatter(Locale.ROOT, false), false);
                XMLReader xmlReader = XMLHelper.newXMLReader();
                FormulaDetectingFilter filter = new FormulaDetectingFilter(collector);
                filter.setParent(xmlReader);
                filter.setContentHandler(handler);
                filter.parse(new InputSource(sheet));
                return collector.result();
            }
        }
    }

    private InputStream selectSupplierSheet(XSSFReader reader) throws IOException, OpenXML4JException {
        XSSFReader.SheetIterator sheets = reader.getSheetIterator();
        InputStream selected = null;
        int sheetCount = 0;
        while (sheets.hasNext()) {
            InputStream input = sheets.next();
            sheetCount++;
            if (sheetCount > policy.maxSheets()) {
                input.close();
                close(selected);
                fail(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
            }
            if (SupplierWorkbookV1Contract.SHEET_NAME.equals(sheets.getSheetName())) {
                selected = input;
            } else {
                input.close();
                close(selected);
                fail(ValidationCode.WORKBOOK_UNEXPECTED_SHEET);
            }
        }
        if (selected == null) {
            fail(ValidationCode.WORKBOOK_SHEET_MISSING);
        }
        return selected;
    }

    private final class ProfileCollector implements XSSFSheetXMLHandler.SheetContentsHandler {

        private final Map<Integer, String> cells = new HashMap<>();
        private final Set<Integer> formulas = new HashSet<>();
        private final List<MutableColumn> columns = new ArrayList<>();
        private int dataRows;

        @Override
        public void startRow(int rowNumber) {
            cells.clear();
            formulas.clear();
        }

        @Override
        public void endRow(int rowNumber) {
            if (rowNumber == 0) {
                readHeaders();
                return;
            }
            if (cells.values().stream().allMatch(String::isEmpty) && formulas.isEmpty()) {
                return;
            }
            dataRows++;
            if (dataRows > policy.maxDataRows()) {
                fail(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
            }
            for (MutableColumn column : columns) {
                String value = cells.getOrDefault(column.index, "");
                if (value.isEmpty()) {
                    column.blankValues++;
                } else {
                    column.nonBlankValues++;
                }
                if (formulas.contains(column.index)) {
                    column.formulaCells++;
                }
            }
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            int column = checkedColumn(cellReference);
            String value = formattedValue == null ? "" : formattedValue;
            if (value.length() > policy.maxCellCharacters()) {
                fail(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
            }
            cells.put(column, value);
        }

        private void formula(String cellReference) {
            formulas.add(checkedColumn(cellReference));
        }

        private int checkedColumn(String cellReference) {
            int column = new CellReference(cellReference).getCol();
            if (column >= policy.maxColumns()) {
                fail(ValidationCode.WORKBOOK_LIMIT_EXCEEDED);
            }
            return column;
        }

        private void readHeaders() {
            if (!formulas.isEmpty()) {
                fail(ValidationCode.FORMULA_CELL_NOT_ALLOWED);
            }
            Set<String> seen = new HashSet<>();
            cells.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
                String header = entry.getValue();
                if (header.isBlank()) {
                    return;
                }
                if (!seen.add(header)) {
                    fail(ValidationCode.HEADER_DUPLICATE);
                }
                columns.add(new MutableColumn(entry.getKey(), header));
            });
        }

        private SupplierWorkbookProfile result() {
            List<SupplierWorkbookColumnProfile> immutable = columns.stream()
                    .map(column -> new SupplierWorkbookColumnProfile(
                            column.index + 1,
                            column.header,
                            SupplierWorkbookV1Contract.BY_NAME.containsKey(column.header),
                            column.nonBlankValues,
                            column.blankValues,
                            column.formulaCells))
                    .toList();
            return new SupplierWorkbookProfile(
                    CONTRACT_VERSION, SupplierWorkbookV1Contract.SHEET_NAME, dataRows, immutable);
        }
    }

    private static final class MutableColumn {
        private final int index;
        private final String header;
        private int nonBlankValues;
        private int blankValues;
        private int formulaCells;

        private MutableColumn(int index, String header) {
            this.index = index;
            this.header = header;
        }
    }

    private static final class FormulaDetectingFilter extends XMLFilterImpl {
        private final ProfileCollector collector;
        private String currentCellReference;

        private FormulaDetectingFilter(ProfileCollector collector) {
            this.collector = collector;
        }

        @Override
        public void startElement(String uri, String localName, String qualifiedName, Attributes attributes)
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

    private static void fail(ValidationCode code) {
        throw new SupplierWorkbookProfilingException(code);
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
            long maximum = Math.multiplyExact(policy.maxUploadBytes(), MAX_EXPANDED_ENTRY_MULTIPLIER);
            ZipSecureFile.setMinInflateRatio(policy.minZipInflateRatio());
            ZipSecureFile.setMaxFileCount(policy.maxZipEntries());
            ZipSecureFile.setMaxEntrySize(maximum);
            ZipSecureFile.setMaxTextSize(maximum);
        }
    }

    private static void close(InputStream input) throws IOException {
        if (input != null) {
            input.close();
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
}
