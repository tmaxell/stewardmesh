package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.util.List;
import java.util.Objects;

/** Complete deterministic mapping proposal bound to one immutable artifact profile. */
public record IntakeMappingSuggestion(
        ImportJobId importJobId,
        IntakeArtifactId artifactId,
        String schemaVersion,
        List<SuggestedColumnMapping> columns,
        List<String> missingRequiredColumns) {

    public IntakeMappingSuggestion {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        Objects.requireNonNull(artifactId, "artifactId must not be null");
        Objects.requireNonNull(schemaVersion, "schemaVersion must not be null");
        if (schemaVersion.isBlank()) {
            throw new IllegalArgumentException("schemaVersion must not be blank");
        }
        columns = List.copyOf(Objects.requireNonNull(columns, "columns must not be null"));
        missingRequiredColumns = List.copyOf(Objects.requireNonNull(
                missingRequiredColumns, "missingRequiredColumns must not be null"));
    }
}
