package io.stewardmesh.masterdata.domain.goldenrecord;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Versioned golden projection of the global supplier legal/economic entity. */
public record SupplierParty(
        SupplierPartyId id,
        GoldenRecordVersion version,
        SurvivorshipRulesetId rulesetId,
        Instant projectedAt,
        Map<GoldenAttributeName, GoldenAttribute> attributes,
        List<SourceAssociationId> sourceAssociations) {

    private static final Set<GoldenAttributeName> REQUIRED =
            Set.of(GoldenAttributeName.LEGAL_NAME, GoldenAttributeName.INN);

    public SupplierParty {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        Objects.requireNonNull(projectedAt, "projectedAt must not be null");
        attributes = GoldenProjectionSupport.attributes(attributes, GoldenEntityType.PARTY, REQUIRED);
        sourceAssociations = List.copyOf(
                Objects.requireNonNull(sourceAssociations, "sourceAssociations must not be null"));
        if (sourceAssociations.isEmpty()) {
            throw new IllegalArgumentException("supplier party requires source associations");
        }
    }
}
