package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.application.port.in.StartSupplierImport;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository;
import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository.IdempotencyRecord;
import io.stewardmesh.masterdata.application.port.out.ImportIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeTelemetry;
import io.stewardmesh.masterdata.application.port.out.IntakeTelemetry.Stage;
import io.stewardmesh.masterdata.application.port.out.StoreIntakeArtifact;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifact;
import java.time.Clock;
import java.util.Objects;

/** Stores original bytes before atomically registering one logical import. */
public final class StartSupplierImportService implements StartSupplierImport {

    private final StoreIntakeArtifact artifactStorage;
    private final IntakeArtifactRepository artifactRepository;
    private final ImportJobRepository importJobRepository;
    private final IdempotencyRepository idempotencyRepository;
    private final ImportIdentityGenerator identityGenerator;
    private final ApplicationTransaction transaction;
    private final IntakeTelemetry telemetry;
    private final Clock clock;

    public StartSupplierImportService(
            StoreIntakeArtifact artifactStorage,
            IntakeArtifactRepository artifactRepository,
            ImportJobRepository importJobRepository,
            IdempotencyRepository idempotencyRepository,
            ImportIdentityGenerator identityGenerator,
            ApplicationTransaction transaction,
            IntakeTelemetry telemetry,
            Clock clock) {
        this.artifactStorage = Objects.requireNonNull(artifactStorage, "artifactStorage must not be null");
        this.artifactRepository =
                Objects.requireNonNull(artifactRepository, "artifactRepository must not be null");
        this.importJobRepository =
                Objects.requireNonNull(importJobRepository, "importJobRepository must not be null");
        this.idempotencyRepository =
                Objects.requireNonNull(idempotencyRepository, "idempotencyRepository must not be null");
        this.identityGenerator =
                Objects.requireNonNull(identityGenerator, "identityGenerator must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public StartSupplierImportResult execute(StartSupplierImportCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        IntakeArtifact stagedArtifact = telemetry.measure(
                Stage.ARTIFACT_STORE, () -> artifactStorage.store(command.workbookContent()));
        try {
            return transaction.execute(() -> register(command, stagedArtifact));
        } catch (ConcurrentImportRegistrationException lostTheRace) {
            // The winning registration is committed by the time this attempt rolled back, so the
            // retry resolves through the ordinary replay path rather than racing again.
            return transaction.execute(() -> register(command, stagedArtifact));
        }
    }

    private StartSupplierImportResult register(
            StartSupplierImportCommand command, IntakeArtifact stagedArtifact) {
        var existing = idempotencyRepository.find(command.requestIdentity());
        if (existing.isPresent()) {
            IdempotencyRecord record = existing.orElseThrow();
            if (!record.artifactSha256().equals(stagedArtifact.sha256())) {
                throw new IdempotencyConflictException();
            }
            ImportJob job = importJobRepository
                    .findById(record.importJobId())
                    .orElseThrow(() -> new IllegalStateException(
                            "idempotency record references a missing import"));
            return new StartSupplierImportResult(job.id(), job.status(), true);
        }

        // Content addressing must resolve in one step. Reading first and inserting after would let
        // two concurrent uploads of identical bytes both miss and both insert.
        IntakeArtifact artifact = artifactRepository.register(stagedArtifact);
        var now = clock.instant();
        ImportJob job = ImportJob.received(
                identityGenerator.nextImportJobId(),
                artifact.id(),
                command.requestIdentity().sourceSystem(),
                now);
        importJobRepository.save(job);
        idempotencyRepository.save(new IdempotencyRecord(
                command.requestIdentity(), job.id(), artifact.sha256(), now));
        return new StartSupplierImportResult(job.id(), job.status(), false);
    }
}
