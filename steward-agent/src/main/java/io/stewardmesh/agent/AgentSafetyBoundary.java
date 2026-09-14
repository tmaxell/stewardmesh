package io.stewardmesh.agent;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Builds the only context that may cross from the supervisor into a reasoner adapter. */
final class AgentSafetyBoundary {

    static final int MAX_EVIDENCE_BYTES = 65_536;
    static final int MAX_TOTAL_EVIDENCE_BYTES = 262_144;
    static final int MAX_NESTING_DEPTH = 12;
    static final int MAX_CONTAINER_ENTRIES = 256;
    static final int MAX_TOTAL_NODES = 2_048;
    static final int MAX_KEY_CHARACTERS = 128;
    static final int MAX_STRING_CHARACTERS = 4_096;

    private final ObjectMapper json = new ObjectMapper();

    AgentReasoningContext prepare(
            AgentGoal goal,
            AgentPhase phase,
            List<AgentObservation> observations,
            int remainingToolCalls,
            Set<String> allowedTools) {
        Objects.requireNonNull(observations, "observations must not be null");
        List<UntrustedToolEvidence> evidence = new ArrayList<>(observations.size());
        int totalBytes = 0;
        for (AgentObservation observation : observations) {
            EncodedEvidence encoded = encode(observation.result());
            totalBytes = Math.addExact(totalBytes, encoded.bytes());
            if (totalBytes > MAX_TOTAL_EVIDENCE_BYTES) {
                throw violation(
                        "UNTRUSTED_EVIDENCE_TOTAL_LIMIT_EXCEEDED",
                        "accumulated MCP evidence exceeds the reasoner context limit");
            }
            evidence.add(new UntrustedToolEvidence(
                    observation.sequence(),
                    observation.phase(),
                    observation.toolName(),
                    observation.decisionCode(),
                    encoded.contentJson(),
                    encoded.sha256()));
        }
        return new AgentReasoningContext(
                TrustedAgentPolicy.standard(),
                goal,
                phase,
                allowedTools,
                evidence,
                remainingToolCalls);
    }

    private EncodedEvidence encode(Map<String, Object> content) {
        NodeBudget budget = new NodeBudget();
        Object canonical = canonicalize(content, 0, budget);
        try {
            byte[] bytes = json.writeValueAsBytes(canonical);
            if (bytes.length > MAX_EVIDENCE_BYTES) {
                throw violation(
                        "UNTRUSTED_EVIDENCE_SIZE_LIMIT_EXCEEDED",
                        "one MCP result exceeds the reasoner evidence limit");
            }
            return new EncodedEvidence(
                    new String(bytes, StandardCharsets.UTF_8), sha256(bytes), bytes.length);
        } catch (JacksonException exception) {
            throw violation(
                    "UNTRUSTED_EVIDENCE_INVALID", "MCP result could not be encoded as bounded JSON");
        }
    }

    private static Object canonicalize(Object value, int depth, NodeBudget budget) {
        budget.add();
        if (depth > MAX_NESTING_DEPTH) {
            throw violation(
                    "UNTRUSTED_EVIDENCE_DEPTH_LIMIT_EXCEEDED",
                    "MCP result nesting exceeds the reasoner evidence limit");
        }
        if (value == null || value instanceof Boolean || value instanceof Number) {
            return value;
        }
        if (value instanceof String text) {
            if (text.length() > MAX_STRING_CHARACTERS) {
                throw violation(
                        "UNTRUSTED_EVIDENCE_STRING_LIMIT_EXCEEDED",
                        "MCP result string exceeds the reasoner evidence limit");
            }
            return text;
        }
        if (value instanceof Map<?, ?> map) {
            requireContainerLimit(map.size());
            Map<String, Object> sorted = new TreeMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key)
                        || key.isBlank()
                        || key.length() > MAX_KEY_CHARACTERS) {
                    throw violation(
                            "UNTRUSTED_EVIDENCE_KEY_INVALID",
                            "MCP result keys must contain 1-128 characters");
                }
                sorted.put(key, canonicalize(entry.getValue(), depth + 1, budget));
            }
            return new LinkedHashMap<>(sorted);
        }
        if (value instanceof Collection<?> collection) {
            requireContainerLimit(collection.size());
            return collection.stream()
                    .map(element -> canonicalize(element, depth + 1, budget))
                    .toList();
        }
        throw violation(
                "UNTRUSTED_EVIDENCE_TYPE_INVALID",
                "MCP result contains a value outside the JSON data model");
    }

    private static void requireContainerLimit(int size) {
        if (size > MAX_CONTAINER_ENTRIES) {
            throw violation(
                    "UNTRUSTED_EVIDENCE_CONTAINER_LIMIT_EXCEEDED",
                    "MCP result container exceeds the reasoner evidence limit");
        }
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is required by the Java runtime", exception);
        }
    }

    private static AgentPolicyViolationException violation(String code, String message) {
        return new AgentPolicyViolationException(code, message);
    }

    private record EncodedEvidence(String contentJson, String sha256, int bytes) {}

    private static final class NodeBudget {
        private int nodes;

        void add() {
            nodes++;
            if (nodes > MAX_TOTAL_NODES) {
                throw violation(
                        "UNTRUSTED_EVIDENCE_NODE_LIMIT_EXCEEDED",
                        "MCP result contains too many values for the reasoner context");
            }
        }
    }
}
