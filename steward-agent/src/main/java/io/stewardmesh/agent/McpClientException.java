package io.stewardmesh.agent;

/** Stable transport or protocol failure at the reference agent's sole external boundary. */
public final class McpClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    McpClientException(String code, String message) {
        super(message);
        this.code = code;
    }

    McpClientException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
