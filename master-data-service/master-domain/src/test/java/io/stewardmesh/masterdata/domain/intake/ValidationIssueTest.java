package io.stewardmesh.masterdata.domain.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ValidationIssueTest {

    @Test
    void derivesSeverityFromStableCode() {
        var warning = new ValidationIssue(
                ValidationCode.HEADER_UNKNOWN, 1, "legacy_note", Map.of("header", "legacy_note"));
        var error = new ValidationIssue(
                ValidationCode.REQUIRED_VALUE_MISSING, 2, "legal_name", Map.of());

        assertEquals(ValidationSeverity.WARNING, warning.severity());
        assertEquals(ValidationSeverity.ERROR, error.severity());
    }

    @Test
    void boundsReportParameters() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new ValidationIssue(
                        ValidationCode.VALUE_FORMAT_INVALID,
                        2,
                        "inn",
                        Map.of("value", "x".repeat(257))));
    }
}
