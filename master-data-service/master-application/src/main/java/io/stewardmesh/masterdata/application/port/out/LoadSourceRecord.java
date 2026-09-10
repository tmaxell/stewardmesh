package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Optional;

/** Reads an immutable source assertion for deterministic identity resolution. */
public interface LoadSourceRecord {

    Optional<SourceRecord> findByIdentity(SourceRecordIdentity identity);
}
