package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Objects;
import java.util.regex.Pattern;

/** Lowercase SHA-256 digest of every execution-relevant plan field. */
public record ActionPlanHash(String value) {

    private static final Pattern SHA_256 = Pattern.compile("[0-9a-f]{64}");

    public ActionPlanHash {
        Objects.requireNonNull(value, "value must not be null");
        if (!SHA_256.matcher(value).matches()) {
            throw new IllegalArgumentException("action plan hash must be lowercase SHA-256 hex");
        }
    }
}
