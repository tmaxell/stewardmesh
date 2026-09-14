package io.stewardmesh.agent;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import tools.jackson.databind.ObjectMapper;

/** Minimal executable entry point for a real Groq -> supervisor -> authenticated MCP demonstration. */
public final class ReferenceAgentDemo {

    private ReferenceAgentDemo() {}

    public static void main(String[] ignored) throws Exception {
        Map<String, String> environment = System.getenv();
        UUID importId = UUID.fromString(required(environment, "STEWARDMESH_IMPORT_ID"));
        URI mcpEndpoint = URI.create(environment.getOrDefault(
                "STEWARDMESH_MCP_URL", "http://localhost:8080/mcp"));
        URI groqBase = URI.create(environment.getOrDefault(
                "GROQ_BASE_URL", GroqStewardReasoner.DEFAULT_BASE_URI.toString()));
        String model = environment.getOrDefault("GROQ_MODEL", GroqStewardReasoner.DEFAULT_MODEL);

        var reasoner = new GroqStewardReasoner(
                groqBase, required(environment, "GROQ_API_KEY"), model);
        var transport = new StreamableHttpMcpCapabilityClient(
                mcpEndpoint, () -> required(environment, "STEWARDMESH_AGENT_TOKEN"));
        var agent = new ReferenceStewardAgent(new SafeRetryingMcpCapabilityClient(transport), reasoner);
        AgentRunResult result = agent.run(new AgentGoal(
                importId, required(environment, "STEWARDMESH_AGENT_OBJECTIVE")));
        Map<String, Object> plan = result.observations().stream()
                .filter(observation -> observation.toolName().equals("create_onboarding_proposal"))
                .findFirst()
                .map(AgentObservation::result)
                .orElseThrow(() -> new IllegalStateException("agent completed without a proposal"));

        System.out.println(new ObjectMapper().writeValueAsString(Map.of(
                "outcome", result.outcomeCode(),
                "toolCalls", result.observations().size(),
                "tools", result.observations().stream().map(AgentObservation::toolName).toList(),
                "planId", plan.get("planId"),
                "planVersion", plan.get("version"),
                "planHash", plan.get("hash"),
                "model", model)));
    }

    private static String required(Map<String, String> environment, String name) {
        String value = environment.get(name);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be set");
        }
        return value.strip();
    }
}
