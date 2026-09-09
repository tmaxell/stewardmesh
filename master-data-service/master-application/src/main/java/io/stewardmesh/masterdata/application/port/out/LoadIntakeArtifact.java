package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.intake.IntakeContent;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;

/** Loads verified original workbook bytes for deterministic processing. */
@FunctionalInterface
public interface LoadIntakeArtifact {

    IntakeContent load(IntakeArtifactId artifactId);
}
