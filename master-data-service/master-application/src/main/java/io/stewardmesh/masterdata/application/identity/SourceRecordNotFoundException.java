package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Objects;

/** Raised when candidate generation targets an unknown immutable source assertion. */
public final class SourceRecordNotFoundException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public SourceRecordNotFoundException(SourceRecordIdentity identity) {
        super("source record was not found: " + describe(identity));
    }

    private static String describe(SourceRecordIdentity identity) {
        Objects.requireNonNull(identity, "identity must not be null");
        return identity.originSystem().value()
                + "/"
                + identity.sourceRecordId()
                + "/"
                + identity.sourceVersion();
    }
}
