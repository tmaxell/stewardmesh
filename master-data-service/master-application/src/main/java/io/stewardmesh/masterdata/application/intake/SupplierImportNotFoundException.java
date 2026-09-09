package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
/** Raised when an import identifier does not resolve to an existing aggregate. */
public final class SupplierImportNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SupplierImportNotFoundException(ImportJobId importJobId) {
        super("supplier import was not found: "
                + java.util.Objects.requireNonNull(importJobId, "importJobId must not be null").value());
    }
}
