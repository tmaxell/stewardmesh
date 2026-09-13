package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.application.intake.SupplierWorkbookProfile;

/** Profiles workbook structure and aggregate completeness without exposing row values. */
@FunctionalInterface
public interface ProfileSupplierWorkbook {

    SupplierWorkbookProfile profile(IntakeContent content);
}
