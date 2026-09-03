package io.stewardmesh.masterdata.domain.intake;

/** Stable machine-readable validation taxonomy published by the workbook contract. */
public enum ValidationCode {
    WORKBOOK_FORMAT_INVALID(ValidationSeverity.ERROR),
    WORKBOOK_LIMIT_EXCEEDED(ValidationSeverity.ERROR),
    WORKBOOK_UNSAFE_CONTENT(ValidationSeverity.ERROR),
    WORKBOOK_SHEET_MISSING(ValidationSeverity.ERROR),
    WORKBOOK_UNEXPECTED_SHEET(ValidationSeverity.ERROR),
    HEADER_MISSING(ValidationSeverity.ERROR),
    HEADER_DUPLICATE(ValidationSeverity.ERROR),
    HEADER_UNKNOWN(ValidationSeverity.WARNING),
    FORMULA_CELL_NOT_ALLOWED(ValidationSeverity.ERROR),
    REQUIRED_VALUE_MISSING(ValidationSeverity.ERROR),
    VALUE_FORMAT_INVALID(ValidationSeverity.ERROR),
    VALUE_TOO_LONG(ValidationSeverity.ERROR),
    VALUE_NOT_ALLOWED(ValidationSeverity.ERROR),
    CONDITIONAL_VALUE_MISSING(ValidationSeverity.ERROR);

    private final ValidationSeverity severity;

    ValidationCode(ValidationSeverity severity) {
        this.severity = severity;
    }

    public ValidationSeverity severity() {
        return severity;
    }
}
