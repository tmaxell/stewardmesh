package io.stewardmesh.masterdata.domain.intake;

/** Lifecycle states for deterministic intake and identity-resolution processing. */
public enum ImportStatus {
    RECEIVED,
    PARSING,
    PARSED,
    VALIDATING,
    VALIDATED,
    MATCHING,
    MATCHED,
    REVIEW_REQUIRED,
    FAILED;

    public boolean isTerminal() {
        return this == MATCHED || this == REVIEW_REQUIRED || this == FAILED;
    }
}
