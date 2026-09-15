package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.goldenrecord.SourceMasterLink;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;

public interface GetSourceMasterLink {

    SourceMasterLink execute(SourceRecordIdentity source);
}
