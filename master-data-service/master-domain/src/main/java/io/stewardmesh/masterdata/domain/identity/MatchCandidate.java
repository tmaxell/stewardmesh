package io.stewardmesh.masterdata.domain.identity;

import java.util.List;
import java.util.UUID;

public sealed interface MatchCandidate permits PartyCandidate, SiteCandidate {

    UUID candidateId();

    MatchEntityType entityType();

    int scoreBasisPoints();

    List<MatchFeature> features();
}
