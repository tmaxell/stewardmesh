package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordState;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.util.Optional;

/** Loads the current write-side state without exposing persistence concerns to mastering. */
public interface LoadGoldenRecordState {

    Optional<GoldenRecordState> findByPartyId(SupplierPartyId partyId);
}
