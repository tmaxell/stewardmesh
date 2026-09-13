package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.application.port.in.ProfileIntakeArtifact;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.LoadIntakeArtifact;
import io.stewardmesh.masterdata.application.port.out.ProfileSupplierWorkbook;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.Objects;

/** Resolves an import to its immutable artifact and delegates safe, read-only profiling. */
public final class ProfileIntakeArtifactService implements ProfileIntakeArtifact {

    private final ImportJobRepository importJobs;
    private final LoadIntakeArtifact artifacts;
    private final ProfileSupplierWorkbook workbooks;

    public ProfileIntakeArtifactService(
            ImportJobRepository importJobs,
            LoadIntakeArtifact artifacts,
            ProfileSupplierWorkbook workbooks) {
        this.importJobs = Objects.requireNonNull(importJobs, "importJobs must not be null");
        this.artifacts = Objects.requireNonNull(artifacts, "artifacts must not be null");
        this.workbooks = Objects.requireNonNull(workbooks, "workbooks must not be null");
    }

    @Override
    public IntakeArtifactProfile execute(ImportJobId importJobId) {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        ImportJob job = importJobs.findById(importJobId)
                .orElseThrow(() -> new SupplierImportNotFoundException(importJobId));
        SupplierWorkbookProfile profile = workbooks.profile(artifacts.load(job.artifactId()));
        return new IntakeArtifactProfile(job.id(), job.artifactId(), profile);
    }
}
