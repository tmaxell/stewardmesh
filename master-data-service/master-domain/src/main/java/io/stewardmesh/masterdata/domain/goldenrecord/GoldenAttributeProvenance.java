package io.stewardmesh.masterdata.domain.goldenrecord;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.time.Instant;
import java.util.Objects;

/** Complete lineage for one selected golden value. */
public record GoldenAttributeProvenance(
        SourceRecordIdentity sourceRecord,
        SourceAssociationId associationId,
        SurvivorshipRule rule,
        SurvivorshipRulesetId rulesetId,
        Instant decidedAt) {

    public GoldenAttributeProvenance {
        Objects.requireNonNull(sourceRecord, "sourceRecord must not be null");
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(rule, "rule must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    }
}
