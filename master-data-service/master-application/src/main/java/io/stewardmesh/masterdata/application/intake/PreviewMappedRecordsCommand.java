package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.List;
import java.util.Objects;

/** User-selected mapping for a bounded, value-free readiness preview. */
public record PreviewMappedRecordsCommand(ImportJobId importJobId, List<ColumnMapping> mappings) {

    public PreviewMappedRecordsCommand {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        mappings = List.copyOf(Objects.requireNonNull(mappings, "mappings must not be null"));
        if (mappings.isEmpty()) {
            throw new IllegalArgumentException("mappings must not be empty");
        }
    }
}
