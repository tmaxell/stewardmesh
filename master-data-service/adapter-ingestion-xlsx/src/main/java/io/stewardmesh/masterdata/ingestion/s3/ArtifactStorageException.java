package io.stewardmesh.masterdata.ingestion.s3;

/** Signals unavailable, missing or inconsistent immutable artifact storage. */
public class ArtifactStorageException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public ArtifactStorageException(String message) {
        super(message);
    }

    public ArtifactStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
