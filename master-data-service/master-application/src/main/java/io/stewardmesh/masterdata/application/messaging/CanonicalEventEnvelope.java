package io.stewardmesh.masterdata.application.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.UUID;

/** Transport-neutral, versioned integration envelope with explicit source lineage. */
public record CanonicalEventEnvelope(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String subjectType,
        String subjectId,
        long entityVersion,
        String originSystem,
        String producer,
        String transportSystem,
        Instant occurredAt,
        Instant publishedAt,
        UUID correlationId,
        Optional<UUID> causationId,
        String traceId,
        DataClassification dataClassification,
        Map<String, String> payload) {

    public CanonicalEventEnvelope {
        Objects.requireNonNull(eventId, "eventId must not be null");
        eventType = text(eventType, "eventType", 64);
        if (schemaVersion != 1) {
            throw new IllegalArgumentException("unsupported schemaVersion");
        }
        subjectType = text(subjectType, "subjectType", 32);
        subjectId = text(subjectId, "subjectId", 128);
        if (entityVersion <= 0) {
            throw new IllegalArgumentException("entityVersion must be positive");
        }
        originSystem = text(originSystem, "originSystem", 64);
        producer = text(producer, "producer", 64);
        transportSystem = text(transportSystem, "transportSystem", 32);
        Objects.requireNonNull(occurredAt, "occurredAt must not be null");
        Objects.requireNonNull(publishedAt, "publishedAt must not be null");
        if (publishedAt.isBefore(occurredAt)) {
            throw new IllegalArgumentException("publishedAt must not precede occurredAt");
        }
        Objects.requireNonNull(correlationId, "correlationId must not be null");
        causationId = Objects.requireNonNull(causationId, "causationId must not be null");
        traceId = text(traceId, "traceId", 128);
        Objects.requireNonNull(dataClassification, "dataClassification must not be null");
        Objects.requireNonNull(payload, "payload must not be null");
        if (payload.size() > 32) {
            throw new IllegalArgumentException("payload exceeds 32 fields");
        }
        var normalized = new TreeMap<String, String>();
        payload.forEach((key, value) -> normalized.put(text(key, "payload key", 64), text(value, "payload value", 512)));
        payload = Map.copyOf(normalized);
    }

    private static String text(String value, String name, int maximum) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return normalized;
    }

    public enum DataClassification {
        INTERNAL
    }
}
