package io.stewardmesh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class StreamableHttpMcpCapabilityClientTest {

    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    @AfterEach
    void stopServer() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void performsAuthenticatedStreamableHttpHandshakeAndReturnsToolContent() throws Exception {
        List<String> methods = new ArrayList<>();
        List<String> authorizations = new ArrayList<>();
        server = server(exchange -> {
            authorizations.add(exchange.getRequestHeaders().getFirst("Authorization"));
            JsonNode request = json.readTree(exchange.getRequestBody());
            methods.add(request.path("method").asString());
            switch (methods.size()) {
                case 1 -> respond(
                        exchange,
                        200,
                        "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}",
                        Map.of("Mcp-Session-Id", "synthetic-session"));
                case 2 -> {
                    assertEquals("synthetic-session", exchange.getRequestHeaders().getFirst("Mcp-Session-Id"));
                    respond(exchange, 202, "", Map.of());
                }
                case 3 -> {
                    assertEquals("profile_intake_artifact", request.path("params").path("name").asString());
                    assertEquals("import-1", request.path("params").path("arguments").path("importId").asString());
                    respond(
                            exchange,
                            200,
                            "data: {\"jsonrpc\":\"2.0\",\"id\":2,\"result\":{"
                                    + "\"isError\":false,\"content\":[{\"type\":\"text\","
                                    + "\"text\":\"{\\\"dataRows\\\":3,\\\"status\\\":\\\"PROFILED\\\",\\\"failureCode\\\":null}\"}]}}\n\n",
                            Map.of());
                }
                default -> throw new AssertionError("unexpected request");
            }
        });
        var client = new StreamableHttpMcpCapabilityClient(endpoint(), () -> "synthetic-token");

        Map<String, Object> result = client.call(
                "profile_intake_artifact", Map.of("importId", "import-1"));

        assertEquals(List.of("initialize", "notifications/initialized", "tools/call"), methods);
        assertTrue(authorizations.stream().allMatch("Bearer synthetic-token"::equals));
        assertEquals(3, result.get("dataRows"));
        assertEquals("PROFILED", result.get("status"));
        assertTrue(result.containsKey("failureCode"));
        assertNull(result.get("failureCode"));
        assertFalse(result.containsKey("rowValues"));
    }

    @Test
    void failsClosedWhenTheServerDoesNotEstablishASession() throws Exception {
        server = server(exchange -> respond(
                exchange,
                200,
                "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":{}}",
                Map.of()));
        var client = new StreamableHttpMcpCapabilityClient(endpoint(), () -> "synthetic-token");

        McpClientException exception = assertThrows(
                McpClientException.class,
                () -> client.call("profile_intake_artifact", Map.of()));

        assertEquals("MCP_SESSION_MISSING", exception.code());
    }

    @Test
    void rejectsHeaderInjectionBeforeAnyNetworkCall() {
        AtomicInteger requests = new AtomicInteger();
        server = server(exchange -> {
            requests.incrementAndGet();
            respond(exchange, 500, "", Map.of());
        });
        var client = new StreamableHttpMcpCapabilityClient(
                endpoint(), () -> "synthetic-token\r\nInjected: true");

        McpClientException exception = assertThrows(
                McpClientException.class,
                () -> client.call("profile_intake_artifact", Map.of()));

        assertEquals("MCP_TOKEN_INVALID", exception.code());
        assertEquals(0, requests.get());
    }

    @Test
    void classifiesTimeoutAuthorizationAndTemporaryServerFailures() {
        server = server(exchange -> {
            Thread.sleep(250);
            respond(exchange, 200, "{}", Map.of("Mcp-Session-Id", "late-session"));
        });
        var timeoutClient = client(Duration.ofMillis(25));
        assertEquals(
                "MCP_CALL_TIMEOUT",
                assertThrows(
                                McpClientException.class,
                                () -> timeoutClient.call("profile_intake_artifact", Map.of()))
                        .code());

        server.stop(0);
        server = server(exchange -> respond(exchange, 401, "", Map.of()));
        assertEquals(
                "MCP_AUTHORIZATION_FAILED",
                assertThrows(
                                McpClientException.class,
                                () -> client(Duration.ofSeconds(1))
                                        .call("profile_intake_artifact", Map.of()))
                        .code());

        server.stop(0);
        server = server(exchange -> respond(exchange, 503, "", Map.of()));
        assertEquals(
                "MCP_SERVER_UNAVAILABLE",
                assertThrows(
                                McpClientException.class,
                                () -> client(Duration.ofSeconds(1))
                                        .call("profile_intake_artifact", Map.of()))
                        .code());
    }

    private HttpServer server(ThrowingHandler handler) {
        try {
            HttpServer created = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            created.createContext("/mcp", exchange -> {
                try {
                    handler.handle(exchange);
                } catch (Exception exception) {
                    exchange.close();
                    throw new IOException("synthetic MCP handler failed", exception);
                }
            });
            created.start();
            return created;
        } catch (IOException exception) {
            throw new AssertionError("synthetic MCP server could not start", exception);
        }
    }

    private URI endpoint() {
        return URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/mcp");
    }

    private StreamableHttpMcpCapabilityClient client(Duration timeout) {
        return new StreamableHttpMcpCapabilityClient(
                endpoint(), HttpClient.newHttpClient(), json, () -> "synthetic-token", timeout);
    }

    private static void respond(
            HttpExchange exchange, int status, String body, Map<String, String> headers)
            throws IOException {
        headers.forEach((name, value) -> exchange.getResponseHeaders().add(name, value));
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    @FunctionalInterface
    private interface ThrowingHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
