package io.stewardmesh.masterdata.domain.intake;

/** Configurable safety limits applied before and while parsing an intake workbook. */
public record ImportPolicy(
        long maxUploadBytes,
        int maxSheets,
        int maxDataRows,
        int maxColumns,
        int maxCellCharacters,
        int maxSharedStrings,
        int maxZipEntries,
        double minZipInflateRatio) {

    public ImportPolicy {
        if (maxUploadBytes <= 0
                || maxSheets <= 0
                || maxDataRows <= 0
                || maxColumns <= 0
                || maxCellCharacters <= 0
                || maxSharedStrings <= 0
                || maxZipEntries <= 0) {
            throw new IllegalArgumentException("import limits must be positive");
        }
        if (!Double.isFinite(minZipInflateRatio)
                || minZipInflateRatio <= 0.0
                || minZipInflateRatio > 1.0) {
            throw new IllegalArgumentException("ZIP inflate ratio must be within (0, 1]");
        }
    }

    public static ImportPolicy supplierWorkbookV1() {
        return new ImportPolicy(5_242_880L, 1, 5_000, 32, 4_096, 50_000, 1_000, 0.01);
    }
}
