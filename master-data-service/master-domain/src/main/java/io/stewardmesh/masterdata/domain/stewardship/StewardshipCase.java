package io.stewardmesh.masterdata.domain.stewardship;

import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.time.Instant;
import java.util.Objects;

/** Open work item keyed by immutable source assertion and scoring ruleset. */
public record StewardshipCase(
        SourceRecordIdentity sourceRecordIdentity,
        MatchRulesetId rulesetId,
        StewardshipCaseReason reason,
        Instant openedAt) {

    public StewardshipCase {
        Objects.requireNonNull(sourceRecordIdentity, "sourceRecordIdentity must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        Objects.requireNonNull(reason, "reason must not be null");
        Objects.requireNonNull(openedAt, "openedAt must not be null");
    }
}
