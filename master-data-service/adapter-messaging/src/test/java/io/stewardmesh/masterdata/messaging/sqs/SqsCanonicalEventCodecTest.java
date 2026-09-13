package io.stewardmesh.masterdata.messaging.sqs;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.messaging.CanonicalEventEnvelope;
import java.io.InputStream;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SqsCanonicalEventCodecTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SqsCanonicalEventCodec codec = new SqsCanonicalEventCodec(mapper);

    @Test
    void roundTripsEveryCanonicalLineageField() {
        var envelope = envelope();
        var decoded = codec.decode(codec.encode(envelope));

        assertEquals(envelope, decoded);
        assertEquals("synthetic-origin", decoded.originSystem());
        assertEquals("sqs", decoded.transportSystem());
    }

    @Test
    void publishedSchemaFreezesRequiredEnvelopeFields() throws Exception {
        try (InputStream stream = getClass().getResourceAsStream(
                "/contracts/events/canonical-event-envelope-v1.json")) {
            JsonNode schema = mapper.readTree(stream);
            assertEquals(1, schema.path("properties").path("schemaVersion").path("const").asInt());
            assertTrue(schema.path("required").toString().contains("originSystem"));
            assertTrue(schema.path("required").toString().contains("causationId"));
            assertTrue(schema.path("additionalProperties").isBoolean());
        }
    }

    @Test
    void boundsCanonicalPayload() {
        var values = new java.util.HashMap<String, String>();
        for (int index = 0; index < 33; index++) values.put("field" + index, "synthetic");
        assertThrows(IllegalArgumentException.class, () -> new CanonicalEventEnvelope(
                UUID.randomUUID(), "Changed", 1, "BUSINESS_UNIT", "synthetic", 1,
                "synthetic-origin", "synthetic-producer", "sqs", Instant.EPOCH, Instant.EPOCH,
                UUID.randomUUID(), Optional.empty(), "trace", CanonicalEventEnvelope.DataClassification.INTERNAL,
                values));
    }

    private static CanonicalEventEnvelope envelope() {
        return new CanonicalEventEnvelope(
                UUID.fromString("00000000-0000-0000-0000-000000001001"),
                "BusinessUnitReferenceChanged", 1, "BUSINESS_UNIT",
                "00000000-0000-0000-0000-000000001002", 3, "synthetic-origin",
                "synthetic-distributor", "sqs", Instant.parse("2026-09-13T10:00:00Z"),
                Instant.parse("2026-09-13T10:00:01Z"),
                UUID.fromString("00000000-0000-0000-0000-000000001003"), Optional.empty(),
                "synthetic-trace-1001", CanonicalEventEnvelope.DataClassification.INTERNAL,
                Map.of("code", "SYNTHETIC-1001"));
    }
}
