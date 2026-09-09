package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ImportRequestIdentity;
import java.util.Objects;

/** Starts one logical supplier workbook import. */
public record StartSupplierImportCommand(
        ImportRequestIdentity requestIdentity, IntakeContent workbookContent) {

    public StartSupplierImportCommand {
        Objects.requireNonNull(requestIdentity, "requestIdentity must not be null");
        Objects.requireNonNull(workbookContent, "workbookContent must not be null");
    }
}
