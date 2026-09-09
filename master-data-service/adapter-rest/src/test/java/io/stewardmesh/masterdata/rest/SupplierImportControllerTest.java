package io.stewardmesh.masterdata.rest;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.stewardmesh.masterdata.application.intake.IdempotencyConflictException;
import io.stewardmesh.masterdata.application.intake.ProcessSupplierImportResult;
import io.stewardmesh.masterdata.application.intake.StartSupplierImportCommand;
import io.stewardmesh.masterdata.application.intake.StartSupplierImportResult;
import io.stewardmesh.masterdata.application.intake.SupplierImportReport;
import io.stewardmesh.masterdata.application.intake.SupplierImportReportQuery;
import io.stewardmesh.masterdata.application.intake.SupplierImportStatus;
import io.stewardmesh.masterdata.application.port.in.GetSupplierImportReport;
import io.stewardmesh.masterdata.application.port.in.GetSupplierImportStatus;
import io.stewardmesh.masterdata.application.port.in.ProcessSupplierImport;
import io.stewardmesh.masterdata.application.port.in.StartSupplierImport;
import io.stewardmesh.masterdata.domain.intake.ImportCounters;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = SupplierImportControllerTest.TestApplication.class)
@AutoConfigureMockMvc
class SupplierImportControllerTest {

    private static final ImportJobId IMPORT_ID = new ImportJobId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StartStub startSupplierImport;

    @Autowired
    private ProcessStub processSupplierImport;

    @Autowired
    private StatusStub getSupplierImportStatus;

    @Autowired
    private ReportStub getSupplierImportReport;

    @BeforeEach
    void resetStubs() {
        startSupplierImport.result = null;
        startSupplierImport.failure = null;
        processSupplierImport.result = null;
        getSupplierImportStatus.result = null;
        getSupplierImportReport.result = null;
    }

    @Test
    void rejectsAnonymousAndInsufficientlyScopedUploadRequests() throws Exception {
        mockMvc.perform(validUpload()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));

        mockMvc.perform(validUpload().with(authenticated()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void acceptsAndProcessesAValidWorkbookWithWriteScope() throws Exception {
        startSupplierImport.result =
                new StartSupplierImportResult(IMPORT_ID, ImportStatus.RECEIVED, false);
        processSupplierImport.result = new ProcessSupplierImportResult(
                IMPORT_ID,
                ImportStatus.VALIDATED,
                new ImportCounters(3, 2, 1, 1, 1));

        mockMvc.perform(validUpload()
                        .header(SupplierImportController.CORRELATION_HEADER, "contract-test-1")
                        .with(writeJwt()))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/supplier-imports/" + IMPORT_ID.value()))
                .andExpect(header().string(
                        SupplierImportController.CORRELATION_HEADER, "contract-test-1"))
                .andExpect(jsonPath("$.importId").value(IMPORT_ID.value().toString()))
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.replayed").value(false));
    }

    @Test
    void validatesRequiredHeadersMediaTypeAndWorkbookSignature() throws Exception {
        mockMvc.perform(multipart("/api/v1/supplier-imports")
                        .file(workbook(SupplierImportController.XLSX_CONTENT_TYPE))
                        .param("sourceSystem", "SYNTHETIC_API")
                        .with(writeJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_FIELD_REQUIRED"));

        mockMvc.perform(multipart("/api/v1/supplier-imports")
                        .file(workbook(MediaType.APPLICATION_OCTET_STREAM_VALUE))
                        .param("sourceSystem", "SYNTHETIC_API")
                        .header("Idempotency-Key", "request-1")
                        .with(writeJwt()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("CONTENT_TYPE_UNSUPPORTED"));

        var invalid = new MockMultipartFile(
                "workbook",
                "synthetic.xlsx",
                SupplierImportController.XLSX_CONTENT_TYPE,
                new byte[] {1, 2, 3, 4});
        mockMvc.perform(multipart("/api/v1/supplier-imports")
                        .file(invalid)
                        .param("sourceSystem", "SYNTHETIC_API")
                        .header("Idempotency-Key", "request-1")
                        .with(writeJwt()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("WORKBOOK_SIGNATURE_INVALID"));
    }

    @Test
    void mapsIdempotencyConflictsToStableProblemDetails() throws Exception {
        startSupplierImport.failure = new IdempotencyConflictException();

        mockMvc.perform(validUpload().with(writeJwt()))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.type").value("urn:stewardmesh:problem:idempotency_conflict"))
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_CONFLICT"));
    }

    @Test
    void returnsStatusAndBoundedReportsWithReadScope() throws Exception {
        var counters = new ImportCounters(3, 2, 1, 1, 1);
        getSupplierImportStatus.result =
                new SupplierImportStatus(IMPORT_ID, ImportStatus.VALIDATED, counters, null);
        getSupplierImportReport.result = new SupplierImportReport(
                IMPORT_ID,
                0,
                20,
                1,
                List.of(new ValidationIssue(
                        ValidationCode.REQUIRED_VALUE_MISSING, 3, "legal_name", Map.of())));

        mockMvc.perform(get("/api/v1/supplier-imports/{id}", IMPORT_ID.value()).with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("VALIDATED"))
                .andExpect(jsonPath("$.counters.acceptedRows").value(2));
        mockMvc.perform(get("/api/v1/supplier-imports/{id}/report", IMPORT_ID.value())
                        .with(readJwt()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalIssues").value(1))
                .andExpect(jsonPath("$.issues[0].code").value("REQUIRED_VALUE_MISSING"))
                .andExpect(jsonPath("$.issues[0].field").value("legal_name"));
    }

    @Test
    void publishesTheVersionedOpenApiPathsWithoutAuthentication() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value("StewardMesh Supplier Intake API"))
                .andExpect(jsonPath("$.paths['/api/v1/supplier-imports']").exists())
                .andExpect(jsonPath("$.paths['/api/v1/supplier-imports/{importId}']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists());
    }

    private static org.springframework.test.web.servlet.request.MockMultipartHttpServletRequestBuilder
            validUpload() {
        return multipart("/api/v1/supplier-imports")
                .file(workbook(SupplierImportController.XLSX_CONTENT_TYPE))
                .param("sourceSystem", "SYNTHETIC_API")
                .header("Idempotency-Key", "request-1");
    }

    private static MockMultipartFile workbook(String contentType) {
        return new MockMultipartFile(
                "workbook",
                "synthetic.xlsx",
                contentType,
                new byte[] {0x50, 0x4b, 0x03, 0x04, 1});
    }

    private static RequestPostProcessor writeJwt() {
        return authentication(new JwtAuthenticationToken(
                jwt(), List.of(new SimpleGrantedAuthority("SCOPE_supplier-import.write"))));
    }

    private static RequestPostProcessor readJwt() {
        return authentication(new JwtAuthenticationToken(
                jwt(), List.of(new SimpleGrantedAuthority("SCOPE_supplier-import.read"))));
    }

    private static RequestPostProcessor authenticated() {
        return authentication(new JwtAuthenticationToken(jwt()));
    }

    private static Jwt jwt() {
        return Jwt.withTokenValue("synthetic-test-token")
                .header("alg", "none")
                .subject("synthetic-test-user")
                .build();
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import({
        SupplierImportController.class,
        IntakeApiExceptionHandler.class,
        IntakeRestConfiguration.class,
        IntakeRestSecurityConfiguration.class,
        IntakeOpenApiConfiguration.class
    })
    static class TestApplication {

        @Bean
        ImportPolicy importPolicy() {
            return ImportPolicy.supplierWorkbookV1();
        }

        @Bean
        MeterRegistry meterRegistry() {
            return new SimpleMeterRegistry();
        }

        @Bean
        StartStub startSupplierImport() {
            return new StartStub();
        }

        @Bean
        ProcessStub processSupplierImport() {
            return new ProcessStub();
        }

        @Bean
        StatusStub getSupplierImportStatus() {
            return new StatusStub();
        }

        @Bean
        ReportStub getSupplierImportReport() {
            return new ReportStub();
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new IllegalArgumentException("synthetic decoder does not accept raw tokens");
            };
        }
    }

    static final class StartStub implements StartSupplierImport {

        private StartSupplierImportResult result;
        private RuntimeException failure;

        @Override
        public StartSupplierImportResult execute(StartSupplierImportCommand command) {
            if (failure != null) {
                throw failure;
            }
            return result;
        }
    }

    static final class ProcessStub implements ProcessSupplierImport {

        private ProcessSupplierImportResult result;

        @Override
        public ProcessSupplierImportResult execute(ImportJobId command) {
            return result;
        }
    }

    static final class StatusStub implements GetSupplierImportStatus {

        private SupplierImportStatus result;

        @Override
        public SupplierImportStatus execute(ImportJobId command) {
            return result;
        }
    }

    static final class ReportStub implements GetSupplierImportReport {

        private SupplierImportReport result;

        @Override
        public SupplierImportReport execute(SupplierImportReportQuery command) {
            return result;
        }
    }
}
