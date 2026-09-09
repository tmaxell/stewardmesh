package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.util.List;
import java.util.Objects;

/** Bounded validation evidence page without unrestricted source-cell content. */
public record SupplierImportReport(
        ImportJobId importJobId,
        int page,
        int size,
        long totalIssues,
        List<ValidationIssue> issues) {

    public SupplierImportReport {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        if (page < 0 || size < 1 || size > 100 || totalIssues < 0) {
            throw new IllegalArgumentException("invalid report pagination");
        }
        issues = List.copyOf(Objects.requireNonNull(issues, "issues must not be null"));
        if (issues.size() > size || issues.size() > totalIssues) {
            throw new IllegalArgumentException("report items exceed the requested or total issue count");
        }
    }
}
