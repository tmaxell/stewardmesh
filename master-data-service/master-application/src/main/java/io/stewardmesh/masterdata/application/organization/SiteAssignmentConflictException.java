package io.stewardmesh.masterdata.application.organization;

public final class SiteAssignmentConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SiteAssignmentConflictException(String message) {
        super(message);
    }

    public SiteAssignmentConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
