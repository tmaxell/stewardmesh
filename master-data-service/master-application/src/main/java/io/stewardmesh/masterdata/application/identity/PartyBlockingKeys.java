package io.stewardmesh.masterdata.application.identity;

import java.util.Objects;

/** Normalized exact identifiers available for party candidate blocking. */
public record PartyBlockingKeys(String inn, String ogrn) {

    public PartyBlockingKeys {
        inn = requireDigits(inn, 10, 12, "inn");
        ogrn = optionalDigits(ogrn, 13, 15, "ogrn");
    }

    private static String requireDigits(String value, int firstLength, int secondLength, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (!(value.length() == firstLength || value.length() == secondLength)
                || !value.chars().allMatch(PartyBlockingKeys::isAsciiDigit)) {
            throw new IllegalArgumentException(name + " must contain a supported normalized identifier");
        }
        return value;
    }

    private static String optionalDigits(
            String value, int firstLength, int secondLength, String name) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return requireDigits(value, firstLength, secondLength, name);
    }

    private static boolean isAsciiDigit(int character) {
        return character >= '0' && character <= '9';
    }
}
