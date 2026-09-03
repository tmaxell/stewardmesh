package io.stewardmesh.masterdata.domain.intake;

/** Lifecycle states for deterministic workbook parsing and validation. */
public enum ImportStatus {
    RECEIVED,
    PARSING,
    PARSED,
    VALIDATING,
    VALIDATED,
    FAILED;

    public boolean isTerminal() {
        return this == VALIDATED || this == FAILED;
    }
}
