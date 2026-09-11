package io.stewardmesh.masterdata.domain.identity;

import java.util.Objects;

/** Normalized source values used for deterministic supplier identity scoring. */
public record SupplierMatchInput(
        String inn,
        String ogrn,
        String legalName,
        String kpp,
        String siteCode,
        String countryCode,
        String city,
        String addressLine) {

    public SupplierMatchInput {
        inn = required(inn, "inn");
        ogrn = optional(ogrn);
        legalName = required(legalName, "legalName");
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
