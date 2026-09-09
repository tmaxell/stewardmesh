package io.stewardmesh.masterdata.rest;

import org.springframework.http.HttpStatus;

final class IntakeRequestException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;
    private final HttpStatus status;

    IntakeRequestException(String code, HttpStatus status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    String code() {
        return code;
    }

    HttpStatus status() {
        return status;
    }
}
