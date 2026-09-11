package io.stewardmesh.masterdata.application.organization;

public final class BusinessUnitConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessUnitConflictException(String message) {
        super(message);
    }
}
