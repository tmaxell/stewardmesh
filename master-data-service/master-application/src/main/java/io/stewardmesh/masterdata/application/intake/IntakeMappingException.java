package io.stewardmesh.masterdata.application.intake;

/** Stable fail-closed rejection of an invalid mapping selection. */
public final class IntakeMappingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    IntakeMappingException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
