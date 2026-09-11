package io.stewardmesh.masterdata.application.organization;

public final class BusinessUnitNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public BusinessUnitNotFoundException() {
        super("business unit was not found");
    }
}
