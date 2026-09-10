package io.stewardmesh.masterdata.domain.identity;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic normalization of supplier source fields before identity resolution. */
public final class SupplierSourceNormalizer {

    public static final NormalizationRulesetId RULESET_ID =
            new NormalizationRulesetId("supplier-source-v1");

    private static final Set<String> CASE_INSENSITIVE_TEXT_FIELDS = Set.of(
            "legal_name", "region", "city", "address_line");
    private static final Set<String> CASE_INSENSITIVE_CODE_FIELDS = Set.of(
            "country_code", "site_code", "procurement_bu_code", "site_purpose");

    public NormalizedSourceValues normalize(Map<String, String> originalValues) {
        Objects.requireNonNull(originalValues, "originalValues must not be null");
        Map<String, String> normalizedValues = new LinkedHashMap<>();
        originalValues.forEach((field, original) -> {
            Objects.requireNonNull(field, "field name must not be null");
            Objects.requireNonNull(original, "source value must not be null");
            String normalized = normalizeWhitespace(Normalizer.normalize(original, Normalizer.Form.NFKC));
            if (!normalized.isEmpty()) {
                if (CASE_INSENSITIVE_TEXT_FIELDS.contains(field)
                        || CASE_INSENSITIVE_CODE_FIELDS.contains(field)) {
                    normalized = normalized.toUpperCase(Locale.ROOT);
                }
                normalizedValues.put(field, normalized);
            }
        });
        return new NormalizedSourceValues(RULESET_ID, normalizedValues);
    }

    private static String normalizeWhitespace(String value) {
        var result = new StringBuilder(value.length());
        boolean pendingSeparator = false;
        for (int offset = 0; offset < value.length(); ) {
            int codePoint = value.codePointAt(offset);
            offset += Character.charCount(codePoint);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSeparator = result.length() > 0;
            } else {
                if (pendingSeparator) {
                    result.append(' ');
                    pendingSeparator = false;
                }
                result.appendCodePoint(codePoint);
            }
        }
        return result.toString();
    }
}
