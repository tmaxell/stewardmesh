package io.stewardmesh.masterdata.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

/**
 * Derives distinct synthetic suppliers from the committed valid fixture.
 *
 * <p>Rewriting the identity columns of a known-good workbook keeps every generated file conformant
 * with the published intake contract without restating that contract in a test. The fixture's shape
 * is preserved: one standalone party plus one party holding two sites.
 */
final class SyntheticSupplierWorkbooks {

    private static final String FIXTURE = "/fixtures/intake/supplier-workbook-v1-valid.xlsx";
    private static final int SOURCE_RECORD_ID = 0;
    private static final int LEGAL_NAME = 2;
    private static final int INN = 3;
    private static final int KPP = 4;
    private static final int POSTAL_CODE = 7;
    private static final int CITY = 9;
    private static final int ADDRESS_LINE = 10;
    private static final int SITE_CODE = 11;

    private SyntheticSupplierWorkbooks() {}

    /** Returns the untouched fixture, whose suppliers are shared by every caller. */
    static byte[] shared() throws IOException {
        try (InputStream fixture = open()) {
            return fixture.readAllBytes();
        }
    }

    /** Returns a workbook whose suppliers cannot match any other tenant index. */
    static byte[] forTenant(int tenant) throws IOException {
        try (InputStream fixture = open();
                Workbook workbook = WorkbookFactory.create(fixture)) {
            Sheet sheet = workbook.getSheetAt(0);
            String standaloneInn = inn(tenant, 2);
            String multiSiteInn = inn(tenant, 5);

            // Rows two and three are the same party at two sites, so they must keep one legal
            // name. Giving them different names would contradict their shared identifier and the
            // import would route to review rather than match.
            rewrite(sheet.getRow(1), tenant, 1, "Alpha", standaloneInn, null, null);
            rewrite(sheet.getRow(2), tenant, 2, "Beta", multiSiteInn, kpp(tenant, 1),
                    "SITE-" + tenant + "-01");
            rewrite(sheet.getRow(3), tenant, 3, "Beta", multiSiteInn, kpp(tenant, 2),
                    "SITE-" + tenant + "-02");

            var bytes = new ByteArrayOutputStream();
            workbook.write(bytes);
            return bytes.toByteArray();
        }
    }

    private static void rewrite(
            Row row,
            int tenant,
            int record,
            String party,
            String inn,
            String kpp,
            String siteCode) {
        set(row, SOURCE_RECORD_ID, "SYN-%d-%04d".formatted(tenant, record));
        set(row, LEGAL_NAME, "Synthetic Tenant %d %s".formatted(tenant, party));
        set(row, INN, inn);
        // Addresses take part in site blocking, so tenants that shared one would become each
        // other's fuzzy candidates and the import would route to review instead of matching.
        set(row, POSTAL_CODE, "1%05d".formatted(tenant * 10 + record));
        set(row, CITY, "Synthetic City %d".formatted(tenant));
        set(row, ADDRESS_LINE, "Synthetic street %d building %d".formatted(tenant, record));
        if (kpp != null) {
            set(row, KPP, kpp);
        }
        if (siteCode != null) {
            set(row, SITE_CODE, siteCode);
        }
    }

    private static void set(Row row, int column, String value) {
        Cell cell = row.getCell(column);
        if (cell == null) {
            cell = row.createCell(column);
        }
        cell.setCellValue(value);
    }

    /** Ten digits, so the generated value satisfies the contract pattern for every tenant. */
    private static String inn(int tenant, int supplier) {
        return "99%06d%02d".formatted(tenant, supplier);
    }

    /** Nine digits, as the contract requires whenever a site is present. */
    private static String kpp(int tenant, int site) {
        return "99%05d%02d".formatted(tenant, site);
    }

    private static InputStream open() throws IOException {
        InputStream fixture = SyntheticSupplierWorkbooks.class.getResourceAsStream(FIXTURE);
        if (fixture == null) {
            throw new IOException("missing intake fixture " + FIXTURE);
        }
        return fixture;
    }
}
