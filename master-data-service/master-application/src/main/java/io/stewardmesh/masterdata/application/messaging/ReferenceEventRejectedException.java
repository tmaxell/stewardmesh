package io.stewardmesh.masterdata.application.messaging;

/** Permanent reference-event rejection that must be quarantined rather than retried. */
public final class ReferenceEventRejectedException extends RuntimeException {
    private static final long serialVersionUID = 1L;
    private final String reasonCode;

    public ReferenceEventRejectedException(String reasonCode, String message) {
        super(message);
        if (reasonCode == null || !reasonCode.matches("[A-Z0-9_]{3,64}")) {
            throw new IllegalArgumentException("reasonCode must be stable upper snake case");
        }
        this.reasonCode = reasonCode;
    }

    public String reasonCode() {
        return reasonCode;
    }
}
