package io.stewardmesh.masterdata.application.intake;

/** Signals that immutable intake storage is unavailable or inconsistent. */
public class IntakeArtifactAccessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public IntakeArtifactAccessException(String message) {
        super(message);
    }

    public IntakeArtifactAccessException(String message, Throwable cause) {
        super(message, cause);
    }
}
