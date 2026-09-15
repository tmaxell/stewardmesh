package io.stewardmesh.agent;

/** Bounded provider failure that never carries prompts, evidence, credentials or response bodies. */
public final class ModelProviderException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public ModelProviderException(String code, String message) {
        super(message);
        this.code = code;
    }

    public ModelProviderException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
