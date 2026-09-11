package io.stewardmesh.masterdata.application.identity;

public final class IdentityResolutionNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IdentityResolutionNotFoundException() {
        super("identity resolution was not found");
    }
}
