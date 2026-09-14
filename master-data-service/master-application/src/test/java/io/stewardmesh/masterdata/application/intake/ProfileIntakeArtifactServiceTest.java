package io.stewardmesh.masterdata.application.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.IntakeArtifactId;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ProfileIntakeArtifactServiceTest {

    private static final ImportJobId IMPORT_ID = new ImportJobId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10a"));
    private static final IntakeArtifactId ARTIFACT_ID = new IntakeArtifactId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10b"));

    @Test
    void bindsAValueFreeWorkbookProfileToTheImmutableArtifact() {
        ImportJob job = ImportJob.received(IMPORT_ID, ARTIFACT_ID, new SourceSystemRef("SYNTHETIC"), Instant.EPOCH);
        IntakeContent content = content();
        SupplierWorkbookProfile workbook = new SupplierWorkbookProfile(
                "1.1.0", "suppliers", 2,
                List.of(new SupplierWorkbookColumnProfile(1, "source_record_id", true, 1, 1, 0)));
        var service = new ProfileIntakeArtifactService(repository(job), requested -> {
            assertEquals(ARTIFACT_ID, requested);
            return content;
        }, supplied -> {
            assertSame(content, supplied);
            return workbook;
        });

        IntakeArtifactProfile result = service.execute(IMPORT_ID);

        assertEquals(IMPORT_ID, result.importJobId());
        assertEquals(ARTIFACT_ID, result.artifactId());
        assertSame(workbook, result.workbook());
    }

    @Test
    void rejectsUnknownImportsBeforeLoadingAnyArtifact() {
        var service = new ProfileIntakeArtifactService(repository(null), ignored -> {
            throw new AssertionError("artifact must not be loaded");
        }, ignored -> {
            throw new AssertionError("workbook must not be profiled");
        });

        assertThrows(SupplierImportNotFoundException.class, () -> service.execute(IMPORT_ID));
    }

    private static ImportJobRepository repository(ImportJob job) {
        return new ImportJobRepository() {
            @Override
            public Optional<ImportJob> findById(ImportJobId importJobId) {
                return Optional.ofNullable(job);
            }

            @Override
            public void save(ImportJob importJob) {
                throw new AssertionError("read-only use case must not save");
            }
        };
    }

    private static IntakeContent content() {
        return new IntakeContent() {
            @Override public String contentType() { return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"; }
            @Override public long sizeBytes() { return 4; }
            @Override public ByteArrayInputStream openStream() { return new ByteArrayInputStream(new byte[] {0x50, 0x4b, 0x03, 0x04}); }
        };
    }
}
