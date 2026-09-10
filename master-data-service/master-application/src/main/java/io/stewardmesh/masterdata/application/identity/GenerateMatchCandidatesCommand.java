package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Objects;

public record GenerateMatchCandidatesCommand(
        SourceRecordIdentity sourceRecordIdentity, int limit) {

    public GenerateMatchCandidatesCommand {
        Objects.requireNonNull(sourceRecordIdentity, "sourceRecordIdentity must not be null");
        if (limit <= 0 || limit > 100) {
            throw new IllegalArgumentException("candidate limit must be between 1 and 100");
        }
    }
}
