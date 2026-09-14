package io.stewardmesh.masterdata.application.intake;

import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** Versioned deterministic canonical columns and conservative known aliases. */
final class SupplierColumnMappingPolicy {

    static final String SCHEMA_VERSION = "supplier-column-mapping-v1";
    static final List<String> CANONICAL_COLUMNS = List.of(
            "source_record_id",
            "source_version",
            "legal_name",
            "inn",
            "kpp",
            "ogrn",
            "country_code",
            "postal_code",
            "region",
            "city",
            "address_line",
            "site_code",
            "procurement_bu_code",
            "site_purpose");
    static final Set<String> REQUIRED_COLUMNS = Set.of(
            "source_record_id",
            "source_version",
            "legal_name",
            "inn",
            "country_code",
            "city",
            "address_line");

    private static final Set<String> CANONICAL = Set.copyOf(CANONICAL_COLUMNS);
    private static final Map<String, String> ALIASES = Map.ofEntries(
            Map.entry("record_id", "source_record_id"),
            Map.entry("version", "source_version"),
            Map.entry("supplier_name", "legal_name"),
            Map.entry("company_name", "legal_name"),
            Map.entry("tax_id", "inn"),
            Map.entry("branch_tax_id", "kpp"),
            Map.entry("registration_number", "ogrn"),
            Map.entry("country", "country_code"),
            Map.entry("zip", "postal_code"),
            Map.entry("state", "region"),
            Map.entry("town", "city"),
            Map.entry("address", "address_line"),
            Map.entry("location_code", "site_code"),
            Map.entry("business_unit", "procurement_bu_code"),
            Map.entry("purpose", "site_purpose"));

    private SupplierColumnMappingPolicy() {}

    static Optional<Target> target(String sourceHeader) {
        String normalized = normalize(sourceHeader);
        if (CANONICAL.contains(normalized)) {
            return Optional.of(new Target(normalized, MappingDecision.EXACT_CANONICAL, 10_000));
        }
        String alias = ALIASES.get(normalized);
        return alias == null
                ? Optional.empty()
                : Optional.of(new Target(alias, MappingDecision.KNOWN_ALIAS, 9_500));
    }

    static boolean canonical(String target) {
        return CANONICAL.contains(target);
    }

    static boolean required(String target) {
        return REQUIRED_COLUMNS.contains(target);
    }

    private static String normalize(String header) {
        String normalized = Normalizer.normalize(header, Normalizer.Form.NFKC)
                .strip()
                .toLowerCase(Locale.ROOT);
        return normalized.replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
    }

    record Target(String column, MappingDecision decision, int confidenceBasisPoints) {}
}
