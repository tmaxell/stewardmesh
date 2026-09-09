package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.application.port.in.ProcessSupplierImport;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.LoadIntakeArtifact;
import io.stewardmesh.masterdata.application.port.out.ParseSupplierWorkbook;
import io.stewardmesh.masterdata.application.port.out.SourceRecordWriter;
import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import io.stewardmesh.masterdata.domain.intake.ValidationSeverity;
import java.time.Clock;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/** Coordinates deterministic parse and persistence stages without holding a transaction over S3. */
public final class ProcessSupplierImportService implements ProcessSupplierImport {

    private static final Set<ValidationCode> FATAL_WORKBOOK_CODES = EnumSet.of(
            ValidationCode.WORKBOOK_FORMAT_INVALID,
            ValidationCode.WORKBOOK_LIMIT_EXCEEDED,
            ValidationCode.WORKBOOK_UNSAFE_CONTENT,
            ValidationCode.WORKBOOK_SHEET_MISSING,
            ValidationCode.WORKBOOK_UNEXPECTED_SHEET,
            ValidationCode.HEADER_MISSING,
            ValidationCode.HEADER_DUPLICATE);

    private final ImportJobRepository importJobRepository;
    private final LoadIntakeArtifact artifactStorage;
    private final ParseSupplierWorkbook workbookParser;
    private final SourceRecordWriter sourceRecordWriter;
    private final ApplicationTransaction transaction;
    private final Clock clock;

    public ProcessSupplierImportService(
            ImportJobRepository importJobRepository,
            LoadIntakeArtifact artifactStorage,
            ParseSupplierWorkbook workbookParser,
            SourceRecordWriter sourceRecordWriter,
            ApplicationTransaction transaction,
            Clock clock) {
        this.importJobRepository =
                Objects.requireNonNull(importJobRepository, "importJobRepository must not be null");
        this.artifactStorage = Objects.requireNonNull(artifactStorage, "artifactStorage must not be null");
        this.workbookParser = Objects.requireNonNull(workbookParser, "workbookParser must not be null");
        this.sourceRecordWriter =
                Objects.requireNonNull(sourceRecordWriter, "sourceRecordWriter must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ProcessSupplierImportResult execute(ImportJobId importJobId) {
        Objects.requireNonNull(importJobId, "importJobId must not be null");
        ImportJob received = find(importJobId);
        if (received.status().isTerminal()) {
            return result(received);
        }

        ImportJob parsing = received.startParsing();
        importJobRepository.save(parsing);
        IntakeContent content;
        try {
            content = artifactStorage.load(parsing.artifactId());
        } catch (IntakeArtifactAccessException exception) {
            ImportJob failed = parsing.fail("ARTIFACT_STORAGE_UNAVAILABLE");
            importJobRepository.save(failed);
            return result(failed);
        }
        SupplierWorkbookParseResult parsed = workbookParser.parse(new SupplierWorkbookParseRequest(
                parsing.id(), parsing.sourceSystem(), clock.instant(), content));

        ImportJob validating = parsing
                .finishParsing(parsed.rowsRead())
                .startValidation();
        importJobRepository.save(validating);

        int warnings = count(parsed, ValidationSeverity.WARNING);
        int errors = count(parsed, ValidationSeverity.ERROR);
        int accepted = parsed.sourceRecords().size();
        int rejected = parsed.rowsRead() - accepted;
        ImportJob completed = hasFatalWorkbookIssue(parsed)
                ? validating.fail("WORKBOOK_VALIDATION_FAILED")
                : validating.finishValidation(accepted, rejected, warnings, errors);

        try {
            return transaction.execute(() -> {
                sourceRecordWriter.writeBatch(
                        completed.id(), parsed.sourceRecords(), parsed.validationIssues());
                importJobRepository.save(completed);
                return result(completed);
            });
        } catch (SourceRecordWriteException exception) {
            ImportJob failed = validating.fail("SOURCE_RECORD_PERSISTENCE_FAILED");
            importJobRepository.save(failed);
            return result(failed);
        }
    }

    private ImportJob find(ImportJobId importJobId) {
        return importJobRepository
                .findById(importJobId)
                .orElseThrow(() -> new SupplierImportNotFoundException(importJobId));
    }

    private static boolean hasFatalWorkbookIssue(SupplierWorkbookParseResult parsed) {
        return parsed.validationIssues().stream()
                .map(ValidationIssue::code)
                .anyMatch(FATAL_WORKBOOK_CODES::contains);
    }

    private static int count(SupplierWorkbookParseResult parsed, ValidationSeverity severity) {
        return Math.toIntExact(parsed.validationIssues().stream()
                .filter(issue -> issue.severity() == severity)
                .count());
    }

    private static ProcessSupplierImportResult result(ImportJob job) {
        return new ProcessSupplierImportResult(job.id(), job.status(), job.counters());
    }
}
