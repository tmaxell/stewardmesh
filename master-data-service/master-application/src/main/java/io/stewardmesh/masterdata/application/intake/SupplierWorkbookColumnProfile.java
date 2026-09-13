package io.stewardmesh.masterdata.application.intake;

import java.util.Objects;

/** Aggregate observations for one workbook column; source row values are deliberately absent. */
public record SupplierWorkbookColumnProfile(
        int position,
        String header,
        boolean canonical,
        int nonBlankValues,
        int blankValues,
        int formulaCells) {

    public SupplierWorkbookColumnProfile {
        if (position < 1) {
            throw new IllegalArgumentException("position must be positive");
        }
        Objects.requireNonNull(header, "header must not be null");
        if (header.isBlank()) {
            throw new IllegalArgumentException("header must not be blank");
        }
        if (nonBlankValues < 0 || blankValues < 0 || formulaCells < 0) {
            throw new IllegalArgumentException("column counts must not be negative");
        }
    }
}
