package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import java.util.UUID;

public record SupplierImportAcceptedResponse(
        UUID importId, ImportStatus status, boolean replayed, String statusUrl, String reportUrl) {}
