package io.stewardmesh.masterdata.bootstrap;

import io.stewardmesh.masterdata.application.intake.GetSupplierImportReportService;
import io.stewardmesh.masterdata.application.intake.GetSupplierImportStatusService;
import io.stewardmesh.masterdata.application.intake.ProcessSupplierImportService;
import io.stewardmesh.masterdata.application.intake.StartSupplierImportService;
import io.stewardmesh.masterdata.application.port.in.GetSupplierImportReport;
import io.stewardmesh.masterdata.application.port.in.GetSupplierImportStatus;
import io.stewardmesh.masterdata.application.port.in.ProcessSupplierImport;
import io.stewardmesh.masterdata.application.port.in.StartSupplierImport;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.IdempotencyRepository;
import io.stewardmesh.masterdata.application.port.out.ImportIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.ImportJobRepository;
import io.stewardmesh.masterdata.application.port.out.IntakeArtifactRepository;
import io.stewardmesh.masterdata.application.port.out.LoadIntakeArtifact;
import io.stewardmesh.masterdata.application.port.out.ParseSupplierWorkbook;
import io.stewardmesh.masterdata.application.port.out.SourceRecordWriter;
import io.stewardmesh.masterdata.application.port.out.StoreIntakeArtifact;
import io.stewardmesh.masterdata.application.port.out.ValidationIssueReader;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class SupplierImportUseCaseConfiguration {

    @Bean
    StartSupplierImport startSupplierImport(
            StoreIntakeArtifact artifactStorage,
            IntakeArtifactRepository artifactRepository,
            ImportJobRepository importJobRepository,
            IdempotencyRepository idempotencyRepository,
            ImportIdentityGenerator identityGenerator,
            ApplicationTransaction transaction,
            Clock clock) {
        return new StartSupplierImportService(
                artifactStorage,
                artifactRepository,
                importJobRepository,
                idempotencyRepository,
                identityGenerator,
                transaction,
                clock);
    }

    @Bean
    ProcessSupplierImport processSupplierImport(
            ImportJobRepository importJobRepository,
            LoadIntakeArtifact artifactStorage,
            ParseSupplierWorkbook workbookParser,
            SourceRecordWriter sourceRecordWriter,
            ApplicationTransaction transaction,
            Clock clock) {
        return new ProcessSupplierImportService(
                importJobRepository,
                artifactStorage,
                workbookParser,
                sourceRecordWriter,
                transaction,
                clock);
    }

    @Bean
    GetSupplierImportStatus getSupplierImportStatus(ImportJobRepository importJobRepository) {
        return new GetSupplierImportStatusService(importJobRepository);
    }

    @Bean
    GetSupplierImportReport getSupplierImportReport(
            ImportJobRepository importJobRepository, ValidationIssueReader validationIssueReader) {
        return new GetSupplierImportReportService(importJobRepository, validationIssueReader);
    }
}
