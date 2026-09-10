package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Objects;

/** One immutable source assertion plus its freshness at orchestration time. */
public record ImportMatchWorkItem(SourceRecordIdentity sourceRecordIdentity, boolean latestVersion) {

    public ImportMatchWorkItem {
        Objects.requireNonNull(sourceRecordIdentity, "sourceRecordIdentity must not be null");
    }
}
