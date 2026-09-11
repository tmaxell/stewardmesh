package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;

public final class StaleSourceRecordException extends IllegalStateException {

    private static final long serialVersionUID = 1L;

    public StaleSourceRecordException(SourceRecordIdentity identity) {
        super("matching requires the latest source record version: " + identity);
    }
}
