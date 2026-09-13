package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Objects;
import java.util.regex.Pattern;

/** Stable machine-readable reason; untrusted source text must not be placed here. */
public record ActionReasonCode(String value) {

    private static final Pattern FORMAT = Pattern.compile("[A-Z][A-Z0-9_]{0,63}");

    public ActionReasonCode {
        Objects.requireNonNull(value, "value must not be null");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "action reason code must be uppercase alphanumeric with underscores");
        }
    }
}
