package io.stewardmesh.masterdata.application.messaging;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Broker-neutral mastered change awaiting at-least-once publication. */
public record OutboxEvent(
        UUID eventId,
        String eventType,
        int schemaVersion,
        String subjectType,
        UUID subjectId,
        long entityVersion,
        String originSystem,
        String producer,
        Instant occurredAt,
        UUID correlationId,
        UUID causationId,
        Map<String, String> payload) {

    public OutboxEvent {
        Objects.requireNonNull(eventId, "event id must not be null");
        eventType = require(eventType, "event type", 64);
        if (schemaVersion <= 0 || entityVersion <= 0) {
            throw new IllegalArgumentException("schema and entity versions must be positive");
        }
        subjectType = require(subjectType, "subject type", 32);
        Objects.requireNonNull(subjectId, "subject id must not be null");
        originSystem = require(originSystem, "origin system", 64);
        producer = require(producer, "producer", 64);
        Objects.requireNonNull(occurredAt, "occurred at must not be null");
        Objects.requireNonNull(correlationId, "correlation id must not be null");
        Objects.requireNonNull(causationId, "causation id must not be null");
        payload = Map.copyOf(Objects.requireNonNull(payload, "payload must not be null"));
    }

    private static String require(String value, String name, int maximum) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return normalized;
    }
}
