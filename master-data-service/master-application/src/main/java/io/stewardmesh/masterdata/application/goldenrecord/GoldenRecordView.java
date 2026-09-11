package io.stewardmesh.masterdata.application.goldenrecord;

import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttribute;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRulesetId;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Transport-neutral current golden-record snapshot. */
public record GoldenRecordView(
        GoldenEntityType entityType,
        UUID entityId,
        UUID partyId,
        UUID addressId,
        long version,
        SurvivorshipRulesetId rulesetId,
        Instant projectedAt,
        List<GoldenAttribute> attributes,
        List<SourceAssociationId> sourceAssociations) {

    public GoldenRecordView {
        Objects.requireNonNull(entityType, "entityType must not be null");
        Objects.requireNonNull(entityId, "entityId must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        Objects.requireNonNull(projectedAt, "projectedAt must not be null");
        attributes = List.copyOf(Objects.requireNonNull(attributes, "attributes must not be null"));
        sourceAssociations = List.copyOf(Objects.requireNonNull(
                sourceAssociations, "sourceAssociations must not be null"));
        if (version <= 0 || (entityType == GoldenEntityType.SITE && addressId == null)) {
            throw new IllegalArgumentException("golden record identity is invalid");
        }
    }
}
