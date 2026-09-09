package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.application.intake.SupplierImportReport;
import java.util.List;
import java.util.UUID;

public record SupplierImportReportResponse(
        UUID importId,
        int page,
        int size,
        long totalIssues,
        List<ValidationIssueResponse> issues) {

    static SupplierImportReportResponse from(SupplierImportReport report) {
        return new SupplierImportReportResponse(
                report.importJobId().value(),
                report.page(),
                report.size(),
                report.totalIssues(),
                report.issues().stream().map(ValidationIssueResponse::from).toList());
    }
}
