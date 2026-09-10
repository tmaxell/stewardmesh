package io.stewardmesh.masterdata.domain.goldenrecord;

import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Versioned operational procure-to-pay context for a party at one address. */
public record SupplierSite(
        SupplierSiteId id,
        SupplierPartyId partyId,
        SupplierAddressId addressId,
        GoldenRecordVersion version,
        SurvivorshipRulesetId rulesetId,
        Instant projectedAt,
        Map<GoldenAttributeName, GoldenAttribute> attributes,
        List<SourceAssociationId> sourceAssociations) {

    private static final Set<GoldenAttributeName> REQUIRED = Set.of(GoldenAttributeName.KPP);

    public SupplierSite {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        Objects.requireNonNull(addressId, "addressId must not be null");
        Objects.requireNonNull(version, "version must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        Objects.requireNonNull(projectedAt, "projectedAt must not be null");
        attributes = GoldenProjectionSupport.attributes(attributes, GoldenEntityType.SITE, REQUIRED);
        sourceAssociations = List.copyOf(
                Objects.requireNonNull(sourceAssociations, "sourceAssociations must not be null"));
        if (sourceAssociations.isEmpty()) {
            throw new IllegalArgumentException("supplier site requires source associations");
        }
    }
}
