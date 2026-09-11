package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.application.intake.IdempotencyConflictException;
import io.stewardmesh.masterdata.application.intake.IntakeArtifactAccessException;
import io.stewardmesh.masterdata.application.intake.SupplierImportNotFoundException;
import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordNotFoundException;
import io.stewardmesh.masterdata.application.identity.IdentityResolutionNotFoundException;
import io.stewardmesh.masterdata.application.identity.MatchCandidateNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class IntakeApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(IntakeApiExceptionHandler.class);

    @ExceptionHandler(IntakeRequestException.class)
    ProblemDetail request(IntakeRequestException exception, HttpServletRequest request) {
        return problem(exception.status(), exception.code(), exception.getMessage(), request);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail invalid(IllegalArgumentException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "REQUEST_INVALID",
                "request fields violate the published contract",
                request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    ProblemDetail missingParameter(
            MissingServletRequestParameterException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.BAD_REQUEST,
                "REQUEST_FIELD_REQUIRED",
                exception.getParameterName() + " is required",
                request);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ProblemDetail oversized(MaxUploadSizeExceededException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONTENT_TOO_LARGE,
                "UPLOAD_TOO_LARGE",
                "workbook exceeds the configured upload limit",
                request);
    }

    @ExceptionHandler(IdempotencyConflictException.class)
    ProblemDetail conflict(IdempotencyConflictException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_CONFLICT",
                "idempotency key is already bound to different workbook content",
                request);
    }

    @ExceptionHandler(SupplierImportNotFoundException.class)
    ProblemDetail notFound(SupplierImportNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "IMPORT_NOT_FOUND",
                "supplier import was not found",
                request);
    }

    @ExceptionHandler(IdentityResolutionNotFoundException.class)
    ProblemDetail identityResolutionNotFound(
            IdentityResolutionNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "IDENTITY_RESOLUTION_NOT_FOUND",
                "identity resolution was not found",
                request);
    }

    @ExceptionHandler(MatchCandidateNotFoundException.class)
    ProblemDetail matchCandidateNotFound(
            MatchCandidateNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "MATCH_CANDIDATE_NOT_FOUND",
                "match candidate was not found",
                request);
    }

    @ExceptionHandler(GoldenRecordNotFoundException.class)
    ProblemDetail goldenRecordNotFound(
            GoldenRecordNotFoundException exception, HttpServletRequest request) {
        return problem(
                HttpStatus.NOT_FOUND,
                "GOLDEN_RECORD_NOT_FOUND",
                "golden record was not found",
                request);
    }

    @ExceptionHandler(IntakeArtifactAccessException.class)
    ProblemDetail storage(IntakeArtifactAccessException exception, HttpServletRequest request) {
        LOGGER.atError()
                .addKeyValue("error_code", "ARTIFACT_STORAGE_UNAVAILABLE")
                .setCause(exception)
                .log("supplier intake storage failed");
        return problem(
                HttpStatus.SERVICE_UNAVAILABLE,
                "ARTIFACT_STORAGE_UNAVAILABLE",
                "intake artifact storage is temporarily unavailable",
                request);
    }

    private static ProblemDetail problem(
            HttpStatus status, String code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(status.getReasonPhrase());
        problem.setType(URI.create("urn:stewardmesh:problem:" + code.toLowerCase(Locale.ROOT)));
        problem.setInstance(URI.create(request.getRequestURI()));
        problem.setProperty("code", code);
        return problem;
    }
}
