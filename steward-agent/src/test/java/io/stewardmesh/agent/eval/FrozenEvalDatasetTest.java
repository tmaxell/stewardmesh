package io.stewardmesh.agent.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;

class FrozenEvalDatasetTest {

    private static final String FROZEN_V1_SHA256 =
            "8fb041b31da53b1240b6d2d3a9f0dad363f968c14c3f9142ad6d6f4da6344dbf";

    @Test
    void loadsTheEighteenSyntheticScenariosFromTheFrozenContract() throws Exception {
        FrozenEvalDataset dataset;
        try (InputStream input = resource()) {
            dataset = new FrozenEvalDatasetLoader().load(input);
        }

        assertEquals("stewardmesh-agent-evals", dataset.datasetId());
        assertEquals("1.0.0", dataset.version());
        assertTrue(dataset.frozen());
        assertEquals("SYNTHETIC_ONLY", dataset.dataPolicy());
        assertEquals(18, dataset.scenarios().size());
        assertEquals(18, dataset.scenarios().stream().map(FrozenEvalScenario::id).distinct().count());
        assertTrue(dataset.scenarios().stream()
                .allMatch(scenario -> scenario.fixtureRef().startsWith("synthetic/")));
        Set<String> ids = dataset.scenarios().stream()
                .map(FrozenEvalScenario::id)
                .collect(Collectors.toUnmodifiableSet());
        assertTrue(ids.containsAll(Set.of(
                "new-supplier",
                "exact-duplicate",
                "fuzzy-duplicate",
                "mutation-without-scope",
                "xlsx-prompt-injection",
                "out-of-order-source-version")));
    }

    @Test
    void locksTheVersionOneDatasetBytes() throws Exception {
        byte[] content;
        try (InputStream input = resource()) {
            content = input.readAllBytes();
        }

        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));

        assertEquals(FROZEN_V1_SHA256, digest);
    }

    @Test
    void rejectsMutableRealOrDuplicateScenarioCollections() {
        ExpectedEvalOutcome outcome = expected();
        assertThrows(
                IllegalArgumentException.class,
                () -> new FrozenEvalDataset(
                        "evals", "1", false, "SYNTHETIC_ONLY", List.of(scenario("one", outcome))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FrozenEvalDataset(
                        "evals", "1", true, "PRODUCTION", List.of(scenario("one", outcome))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FrozenEvalDataset(
                        "evals",
                        "1",
                        true,
                        "SYNTHETIC_ONLY",
                        List.of(scenario("one", outcome), scenario("one", outcome))));
        assertThrows(
                IllegalArgumentException.class,
                () -> new FrozenEvalScenario("one", "production/one", "SAFETY", outcome));
    }

    private InputStream resource() {
        return Objects.requireNonNull(
                getClass().getResourceAsStream("/evals/frozen-agent-scenarios-v1.json"));
    }

    private static FrozenEvalScenario scenario(String id, ExpectedEvalOutcome outcome) {
        return new FrozenEvalScenario(id, "synthetic/" + id, "SAFETY", outcome);
    }

    private static ExpectedEvalOutcome expected() {
        return new ExpectedEvalOutcome(
                "SAFE", "SAFE", true, false, false, false, false, 1, 0, List.of("POLICY"));
    }
}
