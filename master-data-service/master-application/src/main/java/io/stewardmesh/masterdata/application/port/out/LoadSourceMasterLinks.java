package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.goldenrecord.SourceMasterLink;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.List;

public interface LoadSourceMasterLinks {

    /** Reads at most two active links so ambiguous mappings cannot be silently selected. */
    List<SourceMasterLink> findActive(SourceRecordIdentity source);
}
