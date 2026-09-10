package io.stewardmesh.masterdata.domain.identity;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record SiteCandidate(
        SupplierSiteId siteId,
        SupplierPartyId partyId,
        int scoreBasisPoints,
        List<MatchFeature> features)
        implements MatchCandidate {

    public SiteCandidate {
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        scoreBasisPoints = MatchCandidateSupport.requireScore(scoreBasisPoints);
        features = MatchCandidateSupport.immutableFeatures(features);
    }

    @Override
    public UUID candidateId() {
        return siteId.value();
    }

    @Override
    public MatchEntityType entityType() {
        return MatchEntityType.SITE;
    }
}
