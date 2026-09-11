package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.Objects;
import java.util.Set;

public record SiteCandidateBlock(
        SupplierSiteId siteId,
        SupplierPartyId partyId,
        Set<BlockingKeyType> matchedKeys) {

    private static final Set<BlockingKeyType> ALLOWED_KEYS = Set.of(
            BlockingKeyType.SITE_INN_KPP_EXACT,
            BlockingKeyType.SITE_CODE_EXACT,
            BlockingKeyType.SITE_ADDRESS_COARSE);

    public SiteCandidateBlock {
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        matchedKeys = Set.copyOf(Objects.requireNonNull(matchedKeys, "matchedKeys must not be null"));
        if (matchedKeys.isEmpty() || !ALLOWED_KEYS.containsAll(matchedKeys)) {
            throw new IllegalArgumentException("site candidate block must contain site blocking evidence");
        }
    }
}
