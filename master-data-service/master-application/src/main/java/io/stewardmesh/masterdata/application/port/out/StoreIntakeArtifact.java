package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;

/** Stores original workbook bytes immutably and returns their verified metadata. */
@FunctionalInterface
public interface StoreIntakeArtifact {

    IntakeArtifact store(IntakeContent content);
}
