package io.stewardmesh.agent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** Minimal authenticated Streamable HTTP MCP client with one isolated session per tool call. */
public final class StreamableHttpMcpCapabilityClient implements McpCapabilityClient {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SESSION_HEADER = "Mcp-Session-Id";
    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final TypeReference<LinkedHashMap<String, Object>> RESULT_TYPE =
            new TypeReference<>() {};

    private final URI endpoint;
    private final HttpClient http;
    private final ObjectMapper json;
    private final AccessTokenProvider tokens;
    private final Duration timeout;
    private final AtomicLong requestIds = new AtomicLong(1);

    public StreamableHttpMcpCapabilityClient(URI endpoint, AccessTokenProvider tokens) {
        this(
                endpoint,
                HttpClient.newBuilder().connectTimeout(DEFAULT_TIMEOUT).build(),
                new ObjectMapper(),
                tokens,
                DEFAULT_TIMEOUT);
    }

    StreamableHttpMcpCapabilityClient(
            URI endpoint,
            HttpClient http,
            ObjectMapper json,
            AccessTokenProvider tokens,
            Duration timeout) {
        this.endpoint = requireEndpoint(endpoint);
        this.http = Objects.requireNonNull(http, "http must not be null");
        this.json = Objects.requireNonNull(json, "json must not be null");
        this.tokens = Objects.requireNonNull(tokens, "tokens must not be null");
        this.timeout = Objects.requireNonNull(timeout, "timeout must not be null");
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    @Override
    public Map<String, Object> call(String toolName, Map<String, Object> arguments) {
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        String token = requireToken(tokens.accessToken());
        try {
            String sessionId = initialize(token);
            initialized(token, sessionId);
            return invoke(token, sessionId, toolName, arguments);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new McpClientException("MCP_CALL_INTERRUPTED", "MCP call was interrupted", exception);
        } catch (IOException exception) {
            throw new McpClientException("MCP_TRANSPORT_FAILED", "MCP transport failed", exception);
        }
    }

    private String initialize(String token) throws IOException, InterruptedException {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", requestIds.getAndIncrement(),
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of(),
                        "clientInfo", Map.of(
                                "name", "stewardmesh-reference-agent",
                                "version", "1.0")));
        HttpResponse<String> response = send(token, null, request);
        requireSuccess(response, "MCP_INITIALIZE_FAILED");
        return response.headers()
                .firstValue(SESSION_HEADER)
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new McpClientException(
                        "MCP_SESSION_MISSING", "MCP server did not establish a session"));
    }

    private void initialized(String token, String sessionId)
            throws IOException, InterruptedException {
        HttpResponse<String> response = send(
                token,
                sessionId,
                Map.of("jsonrpc", "2.0", "method", "notifications/initialized"));
        requireSuccess(response, "MCP_INITIALIZED_NOTIFICATION_FAILED");
    }

    private Map<String, Object> invoke(
            String token,
            String sessionId,
            String toolName,
            Map<String, Object> arguments)
            throws IOException, InterruptedException {
        Map<String, Object> request = Map.of(
                "jsonrpc", "2.0",
                "id", requestIds.getAndIncrement(),
                "method", "tools/call",
                "params", Map.of("name", toolName, "arguments", arguments));
        HttpResponse<String> response = send(token, sessionId, request);
        requireSuccess(response, "MCP_TOOL_CALL_FAILED");
        JsonNode envelope = json.readTree(protocolPayload(response.body()));
        if (envelope.has("error") || envelope.path("result").path("isError").asBoolean()) {
            throw new McpClientException("MCP_TOOL_REJECTED", "MCP tool returned an error");
        }
        JsonNode content = envelope.path("result").path("content");
        if (!content.isArray() || content.isEmpty() || !content.get(0).has("text")) {
            throw new McpClientException("MCP_RESULT_INVALID", "MCP tool result has no text content");
        }
        return Map.copyOf(json.readValue(content.get(0).path("text").asString(), RESULT_TYPE));
    }

    private HttpResponse<String> send(
            String token, String sessionId, Map<String, Object> payload)
            throws IOException, InterruptedException {
        HttpRequest.Builder request = HttpRequest.newBuilder(endpoint)
                .timeout(timeout)
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(payload)));
        if (sessionId != null) {
            request.header(SESSION_HEADER, sessionId);
            request.header("MCP-Protocol-Version", PROTOCOL_VERSION);
        }
        return http.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static void requireSuccess(HttpResponse<String> response, String code) {
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new McpClientException(code, "MCP server returned HTTP " + response.statusCode());
        }
    }

    private static String protocolPayload(String body) {
        String stripped = Objects.requireNonNull(body, "MCP response body must not be null").strip();
        if (stripped.startsWith("{")) {
            return stripped;
        }
        return stripped.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).strip())
                .findFirst()
                .orElseThrow(() -> new McpClientException(
                        "MCP_RESPONSE_INVALID", "MCP response did not contain JSON event data"));
    }

    private static URI requireEndpoint(URI endpoint) {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        if (!endpoint.isAbsolute()
                || !("http".equalsIgnoreCase(endpoint.getScheme())
                        || "https".equalsIgnoreCase(endpoint.getScheme()))) {
            throw new IllegalArgumentException("endpoint must be an absolute HTTP URI");
        }
        return endpoint;
    }

    private static String requireToken(String token) {
        Objects.requireNonNull(token, "access token must not be null");
        if (token.isBlank() || token.indexOf('\r') >= 0 || token.indexOf('\n') >= 0) {
            throw new McpClientException("MCP_TOKEN_INVALID", "access token is not usable");
        }
        return token;
    }
}
