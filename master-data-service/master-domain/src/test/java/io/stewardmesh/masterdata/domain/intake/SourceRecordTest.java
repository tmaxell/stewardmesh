package io.stewardmesh.masterdata.domain.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceRecordTest {

    @Test
    void retainsIndependentImmutableOriginalAndCanonicalValues() {
        var originals = new HashMap<>(Map.of("legal_name", "  Synthetic Supplier  ", "inn", "9902000005"));
        var canonical = new HashMap<>(Map.of("legal_name", "SYNTHETIC SUPPLIER"));

        var record = new SourceRecord(
                new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC_ERP"), "supplier-42", 2),
                new ImportJobId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10b")),
                Instant.parse("2026-08-28T10:15:30Z"),
                originals,
                canonical);
        originals.put("legal_name", "changed");
        canonical.put("legal_name", "changed");

        assertEquals("  Synthetic Supplier  ", record.originalValues().get("legal_name"));
        assertEquals("SYNTHETIC SUPPLIER", record.canonicalValues().get("legal_name"));
        assertThrows(
                UnsupportedOperationException.class,
                () -> record.originalValues().put("city", "Synthetic City"));
    }

    @Test
    void rejectsCanonicalValuesWithoutOriginalEvidence() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceRecord(
                        new SourceRecordIdentity(
                                new SourceSystemRef("SYNTHETIC_ERP"), "supplier-42", 1),
                        new ImportJobId(
                                UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10b")),
                        Instant.parse("2026-08-28T10:15:30Z"),
                        Map.of("legal_name", "Synthetic Supplier"),
                        Map.of("inn", "9902000005")));
    }
}
