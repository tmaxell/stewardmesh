package io.stewardmesh.masterdata.application.identity;

import java.util.Objects;

/** Normalized exact and coarse context used only for supplier-site blocking. */
public record SiteBlockingKeys(
        String inn,
        String kpp,
        String siteCode,
        String countryCode,
        String postalCode,
        String region,
        String city,
        String addressLine) {

    public SiteBlockingKeys {
        inn = requireDigits(inn, 10, 12, "inn");
        kpp = optionalDigits(kpp, 9, "kpp");
        siteCode = optional(siteCode);
        countryCode = requireText(countryCode, "countryCode");
        postalCode = optional(postalCode);
        region = optional(region);
        city = requireText(city, "city");
        addressLine = requireText(addressLine, "addressLine");
        if (kpp == null && siteCode == null) {
            throw new IllegalArgumentException("site blocking requires KPP or source site code context");
        }
    }

    private static String requireDigits(String value, int firstLength, int secondLength, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (!(value.length() == firstLength || value.length() == secondLength)
                || !value.chars().allMatch(SiteBlockingKeys::isAsciiDigit)) {
            throw new IllegalArgumentException(name + " must contain a supported normalized identifier");
        }
        return value;
    }

    private static String optionalDigits(String value, int length, String name) {
        String optional = optional(value);
        if (optional != null
                && (optional.length() != length
                        || !optional.chars().allMatch(SiteBlockingKeys::isAsciiDigit))) {
            throw new IllegalArgumentException(name + " must contain a supported normalized identifier");
        }
        return optional;
    }

    private static String requireText(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static boolean isAsciiDigit(int character) {
        return character >= '0' && character <= '9';
    }
}
