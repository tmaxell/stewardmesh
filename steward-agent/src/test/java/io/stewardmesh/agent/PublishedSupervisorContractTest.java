package io.stewardmesh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class PublishedSupervisorContractTest {

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void runtimePhasesAllowlistsAndBoundsMatchThePublishedContract() throws Exception {
        JsonNode contract;
        try (InputStream input = Objects.requireNonNull(getClass().getResourceAsStream(
                "/contracts/agent/reference-supervisor-v1.json"))) {
            contract = json.readTree(input);
        }

        assertEquals("1.2.0", contract.path("contractVersion").asString());
        assertEquals(
                Arrays.stream(AgentPhase.values()).map(Enum::name).toList(),
                contract.path("workflow").path("phases").valueStream()
                        .map(JsonNode::asString)
                        .toList());
        assertEquals(
                ReferenceStewardAgent.DEFAULT_MAX_TOOL_CALLS,
                contract.path("workflow").path("maximumToolCalls").asInt());
        for (AgentPhase phase : AgentPhase.values()) {
            Set<String> published = contract.path("capabilities").path(phase.name()).valueStream()
                    .map(JsonNode::asString)
                    .collect(Collectors.toUnmodifiableSet());
            assertEquals(ReferenceStewardAgent.allowedTools(phase), published);
            Set<String> required = contract.path("requiredCapabilitiesBeforeAdvance")
                    .path(phase.name())
                    .valueStream()
                    .map(JsonNode::asString)
                    .collect(Collectors.toUnmodifiableSet());
            assertEquals(ReferenceStewardAgent.requiredTools(phase), required);
            assertTrue(published.containsAll(required));
        }
        Set<String> prohibited = contract.path("prohibitedCapabilities").valueStream()
                .map(JsonNode::asString)
                .collect(Collectors.toUnmodifiableSet());
        assertTrue(prohibited.contains("approve_action_plan"));
        assertTrue(prohibited.contains("execute_approved_plan"));
        for (AgentPhase phase : AgentPhase.values()) {
            ReferenceStewardAgent.allowedTools(phase).forEach(tool ->
                    assertFalse(prohibited.contains(tool)));
        }
        JsonNode safety = contract.path("safetyBoundary");
        assertEquals("UNTRUSTED_TOOL_EVIDENCE", safety.path("toolEvidenceClassification").asString());
        assertEquals("CANONICAL_JSON", safety.path("toolEvidenceEncoding").asString());
        assertEquals("SHA-256", safety.path("toolEvidenceIntegrity").asString());
        JsonNode limits = safety.path("limits");
        assertEquals(AgentSafetyBoundary.MAX_EVIDENCE_BYTES, limits.path("maximumBytesPerResult").asInt());
        assertEquals(
                AgentSafetyBoundary.MAX_TOTAL_EVIDENCE_BYTES,
                limits.path("maximumBytesPerContext").asInt());
        assertEquals(AgentSafetyBoundary.MAX_NESTING_DEPTH, limits.path("maximumNestingDepth").asInt());
        assertEquals(
                AgentSafetyBoundary.MAX_CONTAINER_ENTRIES,
                limits.path("maximumContainerEntries").asInt());
        assertEquals(AgentSafetyBoundary.MAX_TOTAL_NODES, limits.path("maximumNodesPerResult").asInt());
        assertEquals(AgentSafetyBoundary.MAX_KEY_CHARACTERS, limits.path("maximumKeyCharacters").asInt());
        assertEquals(
                AgentSafetyBoundary.MAX_STRING_CHARACTERS,
                limits.path("maximumStringCharacters").asInt());
        assertFalse(TrustedAgentPolicy.standard().instruction().contains("toolResult"));
    }
}
