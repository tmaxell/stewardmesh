package io.stewardmesh.agent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class SafeRetryingMcpCapabilityClientTest {

    @Test
    void retriesAReadOnlyCapabilityWithBoundedLinearBackoff() {
        AtomicInteger attempts = new AtomicInteger();
        List<Duration> delays = new ArrayList<>();
        var client = client((tool, arguments) -> {
            if (attempts.incrementAndGet() < 3) {
                throw failure("MCP_CALL_TIMEOUT");
            }
            return Map.of("status", "RECOVERED");
        }, 3, delays::add);

        Map<String, Object> result = client.call("get_import_status", Map.of("importId", "synthetic"));

        assertEquals("RECOVERED", result.get("status"));
        assertEquals(3, attempts.get());
        assertEquals(List.of(Duration.ofMillis(10), Duration.ofMillis(20)), delays);
    }

    @Test
    void neverRetriesAProposalAfterAnUncertainFailure() {
        AtomicInteger attempts = new AtomicInteger();
        var client = client((tool, arguments) -> {
            attempts.incrementAndGet();
            throw failure("MCP_TRANSPORT_FAILED");
        }, 3, ignored -> {});

        McpClientException exception = assertThrows(
                McpClientException.class,
                () -> client.call("create_onboarding_proposal", Map.of()));

        assertEquals("MCP_TRANSPORT_FAILED", exception.code());
        assertEquals(1, attempts.get());
        assertFalse(SafeRetryingMcpCapabilityClient.retryableTools()
                .contains("create_onboarding_proposal"));
    }

    @Test
    void neverRetriesAuthorizationProtocolOrToolRejections() {
        for (String code : List.of(
                "MCP_AUTHORIZATION_FAILED", "MCP_RESULT_INVALID", "MCP_TOOL_REJECTED")) {
            AtomicInteger attempts = new AtomicInteger();
            var client = client((tool, arguments) -> {
                attempts.incrementAndGet();
                throw failure(code);
            }, 3, ignored -> {});

            assertEquals(
                    code,
                    assertThrows(
                                    McpClientException.class,
                                    () -> client.call("get_action_plan", Map.of()))
                            .code());
            assertEquals(1, attempts.get());
        }
    }

    @Test
    void stopsAfterTheConfiguredAttemptBudget() {
        AtomicInteger attempts = new AtomicInteger();
        List<Duration> delays = new ArrayList<>();
        var client = client((tool, arguments) -> {
            attempts.incrementAndGet();
            throw failure("MCP_SERVER_UNAVAILABLE");
        }, 2, delays::add);

        McpClientException exception = assertThrows(
                McpClientException.class,
                () -> client.call("profile_intake_artifact", Map.of()));

        assertEquals("MCP_SERVER_UNAVAILABLE", exception.code());
        assertEquals(2, attempts.get());
        assertEquals(List.of(Duration.ofMillis(10)), delays);
    }

    @Test
    void preservesInterruptionDuringRetryBackoff() {
        var client = client(
                (tool, arguments) -> {
                    throw failure("MCP_CALL_TIMEOUT");
                },
                3,
                ignored -> {
                    throw new InterruptedException("synthetic interruption");
                });

        try {
            McpClientException exception = assertThrows(
                    McpClientException.class,
                    () -> client.call("get_import_status", Map.of()));
            assertEquals("MCP_RETRY_INTERRUPTED", exception.code());
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void validatesRetryBoundsAndPublishesAnImmutableAllowlist() {
        McpCapabilityClient delegate = (tool, arguments) -> Map.of();
        assertThrows(
                IllegalArgumentException.class,
                () -> new SafeRetryingMcpCapabilityClient(
                        delegate, 0, Duration.ofMillis(1), ignored -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SafeRetryingMcpCapabilityClient(
                        delegate, 4, Duration.ofMillis(1), ignored -> {}));
        assertThrows(
                IllegalArgumentException.class,
                () -> new SafeRetryingMcpCapabilityClient(
                        delegate, 1, Duration.ZERO, ignored -> {}));
        assertThrows(
                UnsupportedOperationException.class,
                () -> SafeRetryingMcpCapabilityClient.retryableTools().add("unsafe"));
    }

    private static SafeRetryingMcpCapabilityClient client(
            McpCapabilityClient delegate,
            int attempts,
            SafeRetryingMcpCapabilityClient.RetryDelay delay) {
        return new SafeRetryingMcpCapabilityClient(
                delegate, attempts, Duration.ofMillis(10), delay);
    }

    private static McpClientException failure(String code) {
        return new McpClientException(code, "synthetic failure");
    }
}
