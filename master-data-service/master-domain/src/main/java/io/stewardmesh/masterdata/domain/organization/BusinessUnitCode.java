package io.stewardmesh.masterdata.domain.organization;

import java.util.Objects;
import java.util.regex.Pattern;

/** Canonical code supplied by the authoritative internal reference source. */
public record BusinessUnitCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z0-9][A-Z0-9._-]{0,63}");

    public BusinessUnitCode {
        Objects.requireNonNull(value, "value must not be null");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("business unit code must be canonical and at most 64 characters");
        }
    }
}
