package io.stewardmesh.masterdata.domain.identity;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record PartyCandidate(
        SupplierPartyId partyId, int scoreBasisPoints, List<MatchFeature> features)
        implements MatchCandidate {

    public PartyCandidate {
        Objects.requireNonNull(partyId, "partyId must not be null");
        scoreBasisPoints = MatchCandidateSupport.requireScore(scoreBasisPoints);
        features = MatchCandidateSupport.immutableFeatures(features);
    }

    @Override
    public UUID candidateId() {
        return partyId.value();
    }

    @Override
    public MatchEntityType entityType() {
        return MatchEntityType.PARTY;
    }
}
