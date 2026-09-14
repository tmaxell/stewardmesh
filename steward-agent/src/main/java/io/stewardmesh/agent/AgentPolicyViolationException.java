package io.stewardmesh.agent;

/** Stable fail-closed rejection of an invalid phase, capability, or run bound. */
public final class AgentPolicyViolationException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    AgentPolicyViolationException(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
