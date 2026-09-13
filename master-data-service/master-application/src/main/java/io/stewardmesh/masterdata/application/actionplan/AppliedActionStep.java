package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.domain.actionplan.ActionType;
import java.util.Objects;
import java.util.UUID;

/** Minimal non-sensitive identity of one committed business effect. */
public record AppliedActionStep(
        int sequence,
        ActionType actionType,
        String subjectType,
        UUID subjectId,
        long subjectVersion,
        String eventType) {

    public AppliedActionStep {
        if (sequence <= 0 || subjectVersion <= 0) {
            throw new IllegalArgumentException("applied step sequence and subject version must be positive");
        }
        Objects.requireNonNull(actionType, "action type must not be null");
        subjectType = bounded(subjectType, "subject type", 32);
        Objects.requireNonNull(subjectId, "subject id must not be null");
        eventType = bounded(eventType, "event type", 64);
    }

    private static String bounded(String value, String name, int maximum) {
        Objects.requireNonNull(value, name + " must not be null");
        String normalized = value.strip();
        if (normalized.isEmpty() || normalized.length() > maximum) {
            throw new IllegalArgumentException(name + " has invalid length");
        }
        return normalized;
    }
}
