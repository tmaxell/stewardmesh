package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Lowercase SHA-256 digest of the proposed content alone. Unlike {@link ActionPlanHash} it omits
 * plan identity, creation time and proposer, so the same mutation proposed twice collides.
 */
public record ActionPlanFingerprint(String value) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ActionPlanFingerprint {
        Objects.requireNonNull(value, "value must not be null");
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException(
                    "action plan fingerprint must be lowercase SHA-256 hex");
        }
    }
}
