package io.stewardmesh.masterdata.ingestion.s3;

import io.stewardmesh.masterdata.application.intake.IntakeArtifactAccessException;

/** Signals unavailable, missing or inconsistent immutable artifact storage. */
public class ArtifactStorageException extends IntakeArtifactAccessException {

    private static final long serialVersionUID = 1L;

    public ArtifactStorageException(String message) {
        super(message);
    }

    public ArtifactStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
