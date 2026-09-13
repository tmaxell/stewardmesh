package io.stewardmesh.masterdata.bootstrap;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.ObjectMapper;

/** Minimal authenticated Streamable HTTP client for black-box MCP acceptance tests. */
final class StreamableMcpTestClient {

    private static final String PROTOCOL_VERSION = "2025-06-18";
    private static final String SESSION_HEADER = "Mcp-Session-Id";

    private final MockMvc mockMvc;
    private final ObjectMapper objectMapper;
    private final AtomicLong requestIds = new AtomicLong(1);

    StreamableMcpTestClient(MockMvc mockMvc, ObjectMapper objectMapper) {
        this.mockMvc = mockMvc;
        this.objectMapper = objectMapper;
    }

    DocumentContext call(String subject, String scope, String tool, Map<String, ?> arguments)
            throws Exception {
        String sessionId = initialize(subject, scope);
        initialized(subject, scope, sessionId);
        long requestId = requestIds.incrementAndGet();
        String request = objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", requestId,
                "method", "tools/call",
                "params", Map.of("name", tool, "arguments", arguments)));
        MvcResult response = mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(token -> token.subject(subject)).authorities(
                                new SimpleGrantedAuthority("SCOPE_" + scope)))
                        .header(SESSION_HEADER, sessionId)
                        .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();
        String protocolResponse = protocolPayload(response.getResponse().getContentAsString());
        Boolean error = JsonPath.read(protocolResponse, "$.result.isError");
        if (Boolean.TRUE.equals(error)) {
            throw new AssertionError("MCP tool failed: " + protocolResponse);
        }
        String toolResult = JsonPath.read(protocolResponse, "$.result.content[0].text");
        return JsonPath.parse(toolResult);
    }

    private String initialize(String subject, String scope) throws Exception {
        long requestId = requestIds.getAndIncrement();
        String request = objectMapper.writeValueAsString(Map.of(
                "jsonrpc", "2.0",
                "id", requestId,
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", PROTOCOL_VERSION,
                        "capabilities", Map.of(),
                        "clientInfo", Map.of("name", "synthetic-phase3-e2e", "version", "1.0"))));
        MvcResult response = mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(token -> token.subject(subject)).authorities(
                                new SimpleGrantedAuthority("SCOPE_" + scope)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(request))
                .andExpect(status().isOk())
                .andReturn();
        String sessionId = response.getResponse().getHeader(SESSION_HEADER);
        if (sessionId == null || sessionId.isBlank()) {
            throw new AssertionError("MCP initialize response did not establish a session");
        }
        return sessionId;
    }

    private void initialized(String subject, String scope, String sessionId) throws Exception {
        String notification = """
                {"jsonrpc":"2.0","method":"notifications/initialized"}
                """;
        mockMvc.perform(post("/mcp")
                        .with(jwt().jwt(token -> token.subject(subject)).authorities(
                                new SimpleGrantedAuthority("SCOPE_" + scope)))
                        .header(SESSION_HEADER, sessionId)
                        .header("MCP-Protocol-Version", PROTOCOL_VERSION)
                        .contentType(MediaType.APPLICATION_JSON)
                        .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                        .content(notification))
                .andExpect(status().isAccepted());
    }

    private static String protocolPayload(String body) {
        String stripped = body.strip();
        if (stripped.startsWith("{")) {
            return stripped;
        }
        return stripped.lines()
                .filter(line -> line.startsWith("data:"))
                .map(line -> line.substring("data:".length()).strip())
                .findFirst()
                .orElseThrow(() -> new AssertionError("MCP response did not contain event data: " + body));
    }
}
