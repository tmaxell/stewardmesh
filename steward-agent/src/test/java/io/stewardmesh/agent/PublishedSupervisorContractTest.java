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

        assertEquals("1.0.0", contract.path("contractVersion").asString());
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
    }
}
