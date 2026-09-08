package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.port.out.ImportIdentityGenerator;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import java.util.UUID;

final class UuidImportIdentityGenerator implements ImportIdentityGenerator {

    @Override
    public ImportJobId nextImportJobId() {
        return new ImportJobId(UUID.randomUUID());
    }

    @Override
    public IntakeArtifactId nextArtifactId() {
        return new IntakeArtifactId(UUID.randomUUID());
    }
}
