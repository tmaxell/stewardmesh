package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;

/** Generates identities independently of a framework or storage adapter. */
public interface ImportIdentityGenerator {

    ImportJobId nextImportJobId();

    IntakeArtifactId nextArtifactId();
}
