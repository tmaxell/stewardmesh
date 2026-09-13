package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.util.Objects;

/** Profile bound to both the public import and its immutable source artifact. */
public record IntakeArtifactProfile(
        ImportJobId importJobId,
        IntakeArtifactId artifactId,
        SupplierWorkbookProfile workbook) {

    public IntakeArtifactProfile {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(workbook, "workbook must not be null");
    }
}
