package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Objects;

public record MatchCandidateBlocks(
        SourceRecordIdentity sourceRecordIdentity,
        CandidateBlockPage<PartyCandidateBlock> parties,
        CandidateBlockPage<SiteCandidateBlock> sites) {

    public MatchCandidateBlocks {
        Objects.requireNonNull(sourceRecordIdentity, "sourceRecordIdentity must not be null");
        Objects.requireNonNull(parties, "parties must not be null");
        Objects.requireNonNull(sites, "sites must not be null");
        if (parties.limit() != sites.limit()) {
            throw new IllegalArgumentException("party and site candidate pages must use the same limit");
        }
    }
}
