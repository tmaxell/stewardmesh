package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.intake.IntakeArtifactProfile;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;

/** Produces a bounded, value-free structural profile of an immutable intake artifact. */
@FunctionalInterface
public interface ProfileIntakeArtifact extends UseCase<ImportJobId, IntakeArtifactProfile> {}
