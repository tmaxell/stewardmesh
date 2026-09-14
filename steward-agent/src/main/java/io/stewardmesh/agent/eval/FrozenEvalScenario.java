package io.stewardmesh.agent.eval;

import java.util.Objects;

/** One opaque synthetic fixture and the outcome against which a subject is graded. */
public record FrozenEvalScenario(
        String id,
        String fixtureRef,
        String category,
        ExpectedEvalOutcome expected) {

    public FrozenEvalScenario {
        id = FrozenEvalDataset.requireText(id, "id");
        fixtureRef = FrozenEvalDataset.requireText(fixtureRef, "fixtureRef");
        category = FrozenEvalDataset.requireText(category, "category");
        Objects.requireNonNull(expected, "expected must not be null");
        if (!fixtureRef.startsWith("synthetic/")) {
            throw new IllegalArgumentException("fixtureRef must use the synthetic namespace");
        }
    }
}
