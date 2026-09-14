package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.util.List;
import java.util.Objects;

/** Value-free preview of mapped-record readiness across the immutable artifact. */
public record MappedRecordsPreview(
        ImportJobId importJobId,
        IntakeArtifactId artifactId,
        int dataRows,
        MappedRecordsReadiness readiness,
        List<String> missingRequiredColumns,
        List<MappedColumnPreview> columns) {

    public MappedRecordsPreview {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        if (dataRows < 0) {
            throw new IllegalArgumentException("dataRows must not be negative");
        }
        Objects.requireNonNull(readiness, "readiness must not be null");
        missingRequiredColumns = List.copyOf(Objects.requireNonNull(
                missingRequiredColumns, "missingRequiredColumns must not be null"));
        columns = List.copyOf(Objects.requireNonNull(columns, "columns must not be null"));
    }
}
