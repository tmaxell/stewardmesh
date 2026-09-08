package io.stewardmesh.masterdata.ingestion.xlsx;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class SupplierWorkbookV1Contract {

    static final String SHEET_NAME = "suppliers";

    static final List<Column> COLUMNS = List.of(
            Column.requiredText("source_record_id", 128),
            Column.requiredPattern("source_version", "^[0-9]+$"),
            Column.requiredText("legal_name", 512),
            Column.requiredPattern("inn", "^(?:[0-9]{10}|[0-9]{12})$"),
            Column.optionalPattern("kpp", "^[0-9]{9}$"),
            Column.optionalPattern("ogrn", "^(?:[0-9]{13}|[0-9]{15})$"),
            Column.requiredPattern("country_code", "^[A-Z]{2}$"),
            Column.optionalText("postal_code", 32),
            Column.optionalText("region", 256),
            Column.requiredText("city", 256),
            Column.requiredText("address_line", 1024),
            Column.optionalText("site_code", 128),
            Column.optionalText("procurement_bu_code", 128),
            Column.optionalAllowed(
                    "site_purpose", Set.of("PURCHASING", "PAY", "SOURCING", "SHIP_FROM")));

    static final Map<String, Column> BY_NAME = COLUMNS.stream()
            .collect(Collectors.toUnmodifiableMap(Column::name, Function.identity()));

    private SupplierWorkbookV1Contract() {}

    record Column(
            String name, boolean valueRequired, Integer maxCharacters, Pattern pattern, Set<String> allowedValues) {

        private static Column requiredText(String name, int maxCharacters) {
            return new Column(name, true, maxCharacters, null, Set.of());
        }

        private static Column optionalText(String name, int maxCharacters) {
            return new Column(name, false, maxCharacters, null, Set.of());
        }

        private static Column requiredPattern(String name, String pattern) {
            return new Column(name, true, null, Pattern.compile(pattern), Set.of());
        }

        private static Column optionalPattern(String name, String pattern) {
            return new Column(name, false, null, Pattern.compile(pattern), Set.of());
        }

        private static Column optionalAllowed(String name, Set<String> allowedValues) {
            return new Column(name, false, null, null, Set.copyOf(allowedValues));
        }
    }
}
