package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.util.Objects;
import java.util.Set;

public record PartyCandidateBlock(
        SupplierPartyId partyId, Set<BlockingKeyType> matchedKeys) {

    private static final Set<BlockingKeyType> ALLOWED_KEYS =
            Set.of(BlockingKeyType.PARTY_INN_EXACT, BlockingKeyType.PARTY_OGRN_EXACT);

    public PartyCandidateBlock {
        Objects.requireNonNull(partyId, "partyId must not be null");
        matchedKeys = Set.copyOf(Objects.requireNonNull(matchedKeys, "matchedKeys must not be null"));
        if (matchedKeys.isEmpty() || !ALLOWED_KEYS.containsAll(matchedKeys)) {
            throw new IllegalArgumentException("party candidate block must contain party blocking evidence");
        }
    }
}
