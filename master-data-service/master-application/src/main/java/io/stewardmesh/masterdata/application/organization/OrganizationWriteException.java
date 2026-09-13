package io.stewardmesh.masterdata.application.organization;

public final class OrganizationWriteException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public OrganizationWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
