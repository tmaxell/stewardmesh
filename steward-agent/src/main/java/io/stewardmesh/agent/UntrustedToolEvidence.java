package io.stewardmesh.agent;

import java.util.Objects;

/** MCP result kept in a data-only envelope and never merged into trusted instructions. */
public record UntrustedToolEvidence(
        int sequence,
        AgentPhase phase,
        String toolName,
        String decisionCode,
        String contentJson,
        String contentSha256) {

    public UntrustedToolEvidence {
        if (sequence < 1) {
            throw new IllegalArgumentException("sequence must be positive");
        }
        Objects.requireNonNull(phase, "phase must not be null");
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(decisionCode, "decisionCode must not be null");
        Objects.requireNonNull(contentJson, "contentJson must not be null");
        if (contentJson.isBlank()) {
            throw new IllegalArgumentException("contentJson must not be blank");
        }
        Objects.requireNonNull(contentSha256, "contentSha256 must not be null");
        if (!contentSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("contentSha256 must be a lowercase SHA-256 digest");
        }
    }

    public String trustClassification() {
        return "UNTRUSTED_TOOL_EVIDENCE";
    }
}
