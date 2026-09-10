package io.stewardmesh.masterdata.domain.identity;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.Objects;

/** Normalized mastered-site values needed by the scorer, independent of persistence. */
public record SiteMatchProfile(
        SupplierSiteId siteId,
        SupplierPartyId partyId,
        String inn,
        String kpp,
        String siteCode,
        String countryCode,
        String city,
        String addressLine) {

    public SiteMatchProfile {
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        inn = required(inn, "inn");
        kpp = optional(kpp);
        siteCode = optional(siteCode);
        countryCode = required(countryCode, "countryCode");
        city = required(city, "city");
        addressLine = required(addressLine, "addressLine");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
