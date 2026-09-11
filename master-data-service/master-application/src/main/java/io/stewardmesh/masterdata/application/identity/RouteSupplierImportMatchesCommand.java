package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.Objects;

public record RouteSupplierImportMatchesCommand(ImportJobId importJobId, int candidateLimit) {

    public RouteSupplierImportMatchesCommand {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        if (candidateLimit <= 0) {
            throw new IllegalArgumentException("candidateLimit must be positive");
        }
    }
}
