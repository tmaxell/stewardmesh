package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Objects;

/** Stable identity of one persisted, reproducible match evaluation. */
public record IdentityResolutionKey(
        SourceRecordIdentity sourceRecordIdentity, MatchRulesetId rulesetId) {

    public IdentityResolutionKey {
        Objects.requireNonNull(sourceRecordIdentity, "sourceRecordIdentity must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
    }
}
