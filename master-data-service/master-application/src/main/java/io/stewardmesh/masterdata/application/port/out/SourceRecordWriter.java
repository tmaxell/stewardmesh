package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.util.List;

/** Writes immutable source assertions and their validation evidence as one batch. */
@FunctionalInterface
public interface SourceRecordWriter {

    void writeBatch(
            ImportJobId importJobId,
            List<SourceRecord> sourceRecords,
            List<ValidationIssue> validationIssues);
}
