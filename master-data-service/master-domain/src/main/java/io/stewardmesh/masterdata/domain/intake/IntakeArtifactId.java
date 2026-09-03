package io.stewardmesh.masterdata.domain.intake;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of an immutable intake artifact. */
public record IntakeArtifactId(UUID value) {

    public IntakeArtifactId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
