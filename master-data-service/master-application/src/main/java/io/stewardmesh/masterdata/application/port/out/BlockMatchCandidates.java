package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.identity.PartyBlockingKeys;
import io.stewardmesh.masterdata.application.identity.PartyCandidateBlock;
import io.stewardmesh.masterdata.application.identity.SiteBlockingKeys;
import io.stewardmesh.masterdata.application.identity.SiteCandidateBlock;
import java.util.List;

/** Persistence-neutral boundary for bounded exact/coarse candidate lookup. */
public interface BlockMatchCandidates {

    List<PartyCandidateBlock> findParties(PartyBlockingKeys keys, int fetchLimit);

    List<SiteCandidateBlock> findSites(SiteBlockingKeys keys, int fetchLimit);
}
