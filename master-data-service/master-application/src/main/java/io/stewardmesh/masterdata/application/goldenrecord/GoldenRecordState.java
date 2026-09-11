package io.stewardmesh.masterdata.application.goldenrecord;

import io.stewardmesh.masterdata.domain.goldenrecord.GoldenSourceAssertion;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Current write-side state required to recalculate one supplier projection. */
public record GoldenRecordState(
        SupplierPartyId partyId,
        long partyVersion,
        List<GoldenSourceAssertion> assertions,
        Map<SupplierAddressId, Long> addressVersions,
        Map<SupplierSiteId, SiteState> sites) {

    public GoldenRecordState {
        Objects.requireNonNull(partyId, "partyId must not be null");
        if (partyVersion <= 0) {
            throw new IllegalArgumentException("partyVersion must be positive");
        }
        assertions = List.copyOf(Objects.requireNonNull(assertions, "assertions must not be null"));
        addressVersions = Map.copyOf(
                Objects.requireNonNull(addressVersions, "addressVersions must not be null"));
        sites = Map.copyOf(Objects.requireNonNull(sites, "sites must not be null"));
    }

    public record SiteState(SupplierAddressId addressId, long version) {

        public SiteState {
            Objects.requireNonNull(addressId, "addressId must not be null");
            if (version <= 0) {
                throw new IllegalArgumentException("site version must be positive");
            }
        }
    }
}
