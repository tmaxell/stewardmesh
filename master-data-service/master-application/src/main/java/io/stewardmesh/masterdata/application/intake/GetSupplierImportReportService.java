package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.application.port.in.GetSupplierImportReport;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.ValidationIssueReader;
import java.util.Objects;

/** Validates import existence before reading a bounded evidence page. */
public final class GetSupplierImportReportService implements GetSupplierImportReport {

    private final ImportJobRepository importJobRepository;
    private final ValidationIssueReader validationIssueReader;

    public GetSupplierImportReportService(
            ImportJobRepository importJobRepository, ValidationIssueReader validationIssueReader) {
        this.importJobRepository =
                Objects.requireNonNull(importJobRepository, "importJobRepository must not be null");
        this.validationIssueReader =
                Objects.requireNonNull(validationIssueReader, "validationIssueReader must not be null");
    }

    @Override
    public SupplierImportReport execute(SupplierImportReportQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        if (importJobRepository.findById(query.importJobId()).isEmpty()) {
            throw new SupplierImportNotFoundException(query.importJobId());
        }
        return validationIssueReader.read(query);
    }
}
