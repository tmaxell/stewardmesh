package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.time.Instant;
import java.util.Objects;

/** Context required to turn one verified workbook into source assertions. */
public record SupplierWorkbookParseRequest(
        ImportJobId importJobId,
        SourceSystemRef originSystem,
        Instant ingestedAt,
        IntakeContent content) {

    public SupplierWorkbookParseRequest {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(originSystem, "originSystem must not be null");
        Objects.requireNonNull(ingestedAt, "ingestedAt must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
