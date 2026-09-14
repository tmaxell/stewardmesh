package io.stewardmesh.agent;

import java.util.Objects;
import java.util.UUID;

/** Bounded onboarding goal supplied to the reference workflow. */
public record AgentGoal(UUID importId, String objective) {

    public static final int MAX_OBJECTIVE_CHARACTERS = 512;

    public AgentGoal {
        Objects.requireNonNull(importId, "importId must not be null");
        Objects.requireNonNull(objective, "objective must not be null");
        objective = objective.strip();
        if (objective.isEmpty() || objective.length() > MAX_OBJECTIVE_CHARACTERS) {
            throw new IllegalArgumentException("objective must contain 1-512 characters");
        }
    }
}
