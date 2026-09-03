package io.stewardmesh.masterdata.domain.intake;

/** Bounded import outcome counters without row-level supplier data. */
public record ImportCounters(
        int totalRows, int acceptedRows, int rejectedRows, int warningCount, int errorCount) {

    public static final ImportCounters EMPTY = new ImportCounters(0, 0, 0, 0, 0);

    public ImportCounters {
        if (totalRows < 0
                || acceptedRows < 0
                || rejectedRows < 0
                || warningCount < 0
                || errorCount < 0) {
            throw new IllegalArgumentException("import counters must not be negative");
        }
        if ((long) acceptedRows + rejectedRows > totalRows) {
            throw new IllegalArgumentException("accepted and rejected rows must not exceed total rows");
        }
    }

    public static ImportCounters parsed(int totalRows) {
        return new ImportCounters(totalRows, 0, 0, 0, 0);
    }

    public static ImportCounters validated(
            int totalRows, int acceptedRows, int rejectedRows, int warningCount, int errorCount) {
        if ((long) acceptedRows + rejectedRows != totalRows) {
            throw new IllegalArgumentException("validated row counts must account for every parsed row");
        }
        return new ImportCounters(totalRows, acceptedRows, rejectedRows, warningCount, errorCount);
    }
}
