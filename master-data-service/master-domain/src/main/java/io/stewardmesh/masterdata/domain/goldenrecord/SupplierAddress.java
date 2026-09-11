package io.stewardmesh.masterdata.domain.goldenrecord;

import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Versioned supplier address assertion, deliberately distinct from an operational site. */
public record SupplierAddress(
        SupplierAddressId id,
        SupplierPartyId partyId,
        GoldenRecordVersion version,
        SurvivorshipRulesetId rulesetId,
        Instant projectedAt,
        Map<GoldenAttributeName, GoldenAttribute> attributes,
        List<SourceAssociationId> sourceAssociations) {

    private static final Set<GoldenAttributeName> REQUIRED = Set.of(
            GoldenAttributeName.COUNTRY_CODE,
            GoldenAttributeName.CITY,
            GoldenAttributeName.ADDRESS_LINE);

    public SupplierAddress {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        Objects.requireNonNull(projectedAt, "projectedAt must not be null");
        attributes = GoldenProjectionSupport.attributes(attributes, GoldenEntityType.ADDRESS, REQUIRED);
        sourceAssociations = List.copyOf(
                Objects.requireNonNull(sourceAssociations, "sourceAssociations must not be null"));
        if (sourceAssociations.isEmpty()) {
            throw new IllegalArgumentException("supplier address requires source associations");
        }
    }
}
