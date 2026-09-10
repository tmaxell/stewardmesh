package io.stewardmesh.masterdata.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SupplierSourceNormalizerTest {

    private final SupplierSourceNormalizer normalizer = new SupplierSourceNormalizer();

    @Test
    void normalizesSupplierIdentityAndLocationFieldsDeterministically() {
        Map<String, String> original = new LinkedHashMap<>();
        original.put("source_record_id", "  Source-01  ");
        original.put("legal_name", "  Ооо\u00a0«Синтетик   Альфа»  ");
        original.put("inn", "  ９９０１０００００２ ");
        original.put("country_code", " ru ");
        original.put("city", " Тестоград\tЦентр ");
        original.put("site_code", " site-a ");
        original.put("ogrn", "  ");

        var first = normalizer.normalize(original);
        var second = normalizer.normalize(original);

        assertEquals(SupplierSourceNormalizer.RULESET_ID, first.rulesetId());
        assertEquals(first, second);
        assertEquals("Source-01", first.values().get("source_record_id"));
        assertEquals("ООО «СИНТЕТИК АЛЬФА»", first.values().get("legal_name"));
        assertEquals("9901000002", first.values().get("inn"));
        assertEquals("RU", first.values().get("country_code"));
        assertEquals("ТЕСТОГРАД ЦЕНТР", first.values().get("city"));
        assertEquals("SITE-A", first.values().get("site_code"));
        assertFalse(first.values().containsKey("ogrn"));
    }

    @Test
    void doesNotMutateOrExposeMutableSourceMaps() {
        var original = new LinkedHashMap<>(Map.of("legal_name", "Synthetic Supplier"));

        var normalized = normalizer.normalize(original);
        original.put("legal_name", "Changed");

        assertEquals("SYNTHETIC SUPPLIER", normalized.values().get("legal_name"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> normalized.values().put("legal_name", "Changed"));
    }

    @Test
    void rejectsUnversionedRulesetIdentifiers() {
        assertThrows(IllegalArgumentException.class, () -> new NormalizationRulesetId("supplier-source"));
    }
}
