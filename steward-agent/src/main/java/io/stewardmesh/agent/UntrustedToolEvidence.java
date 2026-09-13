package io.stewardmesh.agent;

import java.util.Map;
import java.util.Objects;

/** MCP result kept in a data-only envelope and never merged into trusted instructions. */
public record UntrustedToolEvidence(
        int sequence,
        AgentPhase phase,
        String toolName,
        String decisionCode,
        Map<String, Object> content) {

    public UntrustedToolEvidence {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(decisionCode, "decisionCode must not be null");
        content = Map.copyOf(Objects.requireNonNull(content, "content must not be null"));
    }

    public String trustClassification() {
        return "UNTRUSTED_TOOL_EVIDENCE";
    }
}
