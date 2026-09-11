package io.stewardmesh.masterdata.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.stewardmesh.masterdata.application.intake.ProcessSupplierImportResult;
import io.stewardmesh.masterdata.application.intake.StartSupplierImportCommand;
import io.stewardmesh.masterdata.application.intake.SupplierImportReportQuery;
import io.stewardmesh.masterdata.application.identity.CandidateBlockingPolicy;
import io.stewardmesh.masterdata.application.identity.RouteSupplierImportMatchesCommand;
import io.stewardmesh.masterdata.application.port.in.GetSupplierImportReport;
import io.stewardmesh.masterdata.application.port.in.GetSupplierImportStatus;
import io.stewardmesh.masterdata.application.port.in.ProcessSupplierImport;
import io.stewardmesh.masterdata.application.port.in.RouteSupplierImportMatches;
import io.stewardmesh.masterdata.application.port.in.StartSupplierImport;
import io.stewardmesh.masterdata.domain.intake.IdempotencyKey;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.ImportRequestIdentity;
import io.stewardmesh.masterdata.domain.intake.ImportStatus;
import io.stewardmesh.masterdata.domain.intake.ImportPolicy;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import java.io.IOException;
import java.net.URI;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/supplier-imports")
@SecurityRequirement(name = "bearerAuth")
public class SupplierImportController {

    static final String XLSX_CONTENT_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
    static final String CORRELATION_HEADER = "X-Correlation-ID";

    private static final Logger LOGGER = LoggerFactory.getLogger(SupplierImportController.class);
    private static final byte[] ZIP_SIGNATURE = {0x50, 0x4b, 0x03, 0x04};
    private static final Pattern CORRELATION_ID = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private final StartSupplierImport startSupplierImport;
    private final ProcessSupplierImport processSupplierImport;
    private final RouteSupplierImportMatches routeSupplierImportMatches;
    private final GetSupplierImportStatus getSupplierImportStatus;
    private final GetSupplierImportReport getSupplierImportReport;
    private final ImportPolicy importPolicy;
    private final CandidateBlockingPolicy candidateBlockingPolicy;
    private final IntakeApiTelemetry telemetry;

    public SupplierImportController(
            StartSupplierImport startSupplierImport,
            ProcessSupplierImport processSupplierImport,
            RouteSupplierImportMatches routeSupplierImportMatches,
            GetSupplierImportStatus getSupplierImportStatus,
            GetSupplierImportReport getSupplierImportReport,
            ImportPolicy importPolicy,
            CandidateBlockingPolicy candidateBlockingPolicy,
            IntakeApiTelemetry telemetry) {
        this.startSupplierImport = startSupplierImport;
        this.processSupplierImport = processSupplierImport;
        this.routeSupplierImportMatches = routeSupplierImportMatches;
        this.getSupplierImportStatus = getSupplierImportStatus;
        this.getSupplierImportReport = getSupplierImportReport;
        this.importPolicy = importPolicy;
        this.candidateBlockingPolicy = candidateBlockingPolicy;
        this.telemetry = telemetry;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Accept and process one supplier workbook")
    public ResponseEntity<SupplierImportAcceptedResponse> upload(
            @Parameter(in = ParameterIn.HEADER, required = true)
                    @RequestHeader(name = "Idempotency-Key", required = false)
                    String idempotencyKey,
            @RequestParam(name = "sourceSystem", required = false) String sourceSystem,
            @RequestPart(name = "workbook", required = false) MultipartFile workbook,
            @RequestHeader(name = CORRELATION_HEADER, required = false) String correlationHeader) {
        String correlationId = correlationId(correlationHeader);
        MultipartIntakeContent content = content(workbook);
        var identity = new ImportRequestIdentity(
                new SourceSystemRef(required(sourceSystem, "sourceSystem")),
                new IdempotencyKey(required(idempotencyKey, "Idempotency-Key")));
        long startedAt = telemetry.start();
        var started = startSupplierImport.execute(new StartSupplierImportCommand(identity, content));
        telemetry.started(content.sizeBytes(), started.replayed());

        ImportStatus finalStatus = started.status();
        if (started.status() == ImportStatus.RECEIVED) {
            ProcessSupplierImportResult processed = processSupplierImport.execute(started.importJobId());
            telemetry.completed(startedAt, processed);
            finalStatus = processed.status();
            if (processed.status() == ImportStatus.VALIDATED) {
                finalStatus = routeSupplierImportMatches
                        .execute(new RouteSupplierImportMatchesCommand(
                                started.importJobId(), candidateBlockingPolicy.maximumCandidates()))
                        .status();
            }
        }

        String statusUrl = path(started.importJobId());
        LOGGER.atInfo()
                .addKeyValue("correlation_id", correlationId)
                .addKeyValue("import_id", started.importJobId().value())
                .addKeyValue("source_system", identity.sourceSystem().value())
                .addKeyValue("status", finalStatus)
                .addKeyValue("replayed", started.replayed())
                .log("supplier import accepted");
        return ResponseEntity.accepted()
                .location(URI.create(statusUrl))
                .header(CORRELATION_HEADER, correlationId)
                .body(new SupplierImportAcceptedResponse(
                        started.importJobId().value(),
                        finalStatus,
                        started.replayed(),
                        statusUrl,
                        statusUrl + "/report"));
    }

    @GetMapping("/{importId}")
    @Operation(summary = "Read supplier import status")
    public SupplierImportStatusResponse status(
            @org.springframework.web.bind.annotation.PathVariable String importId) {
        return SupplierImportStatusResponse.from(
                getSupplierImportStatus.execute(importId(importId)));
    }

    @GetMapping("/{importId}/report")
    @Operation(summary = "Read one bounded page of validation evidence")
    public SupplierImportReportResponse report(
            @org.springframework.web.bind.annotation.PathVariable String importId,
            @RequestParam(defaultValue = "0") String page,
            @RequestParam(defaultValue = "20") String size) {
        var query = new SupplierImportReportQuery(
                importId(importId), number(page, "page"), number(size, "size"));
        return SupplierImportReportResponse.from(getSupplierImportReport.execute(query));
    }

    private MultipartIntakeContent content(MultipartFile workbook) {
        if (workbook == null || workbook.isEmpty()) {
            throw request("WORKBOOK_REQUIRED", HttpStatus.BAD_REQUEST, "workbook part is required");
        }
        if (!XLSX_CONTENT_TYPE.equals(workbook.getContentType())) {
            throw request(
                    "CONTENT_TYPE_UNSUPPORTED",
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "only the XLSX media type is supported");
        }
        if (workbook.getSize() > importPolicy.maxUploadBytes()) {
            throw request(
                    "UPLOAD_TOO_LARGE",
                    HttpStatus.CONTENT_TOO_LARGE,
                    "workbook exceeds the configured upload limit");
        }
        try (var input = workbook.getInputStream()) {
            if (!java.util.Arrays.equals(ZIP_SIGNATURE, input.readNBytes(ZIP_SIGNATURE.length))) {
                throw request(
                        "WORKBOOK_SIGNATURE_INVALID",
                        HttpStatus.BAD_REQUEST,
                        "workbook does not have an XLSX ZIP signature");
            }
        } catch (IOException exception) {
            throw request(
                    "WORKBOOK_UNREADABLE", HttpStatus.BAD_REQUEST, "workbook content cannot be read");
        }
        return new MultipartIntakeContent(workbook, XLSX_CONTENT_TYPE);
    }

    private static ImportJobId importId(String value) {
        try {
            return new ImportJobId(UUID.fromString(value));
        } catch (IllegalArgumentException exception) {
            throw request("IMPORT_ID_INVALID", HttpStatus.BAD_REQUEST, "importId must be a UUID");
        }
    }

    private static int number(String value, String name) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException exception) {
            throw request("PAGINATION_INVALID", HttpStatus.BAD_REQUEST, name + " must be an integer");
        }
    }

    private static String required(String value, String name) {
        if (value == null || value.isBlank()) {
            throw request("REQUEST_FIELD_REQUIRED", HttpStatus.BAD_REQUEST, name + " is required");
        }
        return value;
    }

    private static String correlationId(String supplied) {
        if (supplied == null || supplied.isBlank()) {
            return UUID.randomUUID().toString();
        }
        if (!CORRELATION_ID.matcher(supplied).matches()) {
            throw request(
                    "CORRELATION_ID_INVALID",
                    HttpStatus.BAD_REQUEST,
                    "X-Correlation-ID has an invalid format");
        }
        return supplied;
    }

    private static String path(ImportJobId importJobId) {
        return "/api/v1/supplier-imports/" + importJobId.value();
    }

    private static IntakeRequestException request(String code, HttpStatus status, String detail) {
        return new IntakeRequestException(code, status, detail);
    }
}
