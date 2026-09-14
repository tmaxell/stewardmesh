package io.stewardmesh.agent;

import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Bounded retry decorator that never repeats a mutation after an uncertain response. */
public final class SafeRetryingMcpCapabilityClient implements McpCapabilityClient {

    public static final int DEFAULT_MAXIMUM_ATTEMPTS = 3;
    public static final Duration DEFAULT_INITIAL_BACKOFF = Duration.ofMillis(100);

    private static final Set<String> RETRYABLE_TOOLS = Set.of(
            "profile_intake_artifact",
            "suggest_schema_mapping",
            "preview_mapped_records",
            "get_import_status",
            "find_party_candidates",
            "find_site_candidates",
            "explain_match",
            "simulate_onboarding_plan",
            "get_action_plan",
            "verify_onboarding_result");
    private static final Set<String> RETRYABLE_FAILURES = Set.of(
            "MCP_CALL_TIMEOUT", "MCP_TRANSPORT_FAILED", "MCP_SERVER_UNAVAILABLE");

    private final McpCapabilityClient delegate;
    private final int maximumAttempts;
    private final Duration initialBackoff;
    private final RetryDelay delay;
    private final AgentRuntimeTelemetry telemetry;

    public SafeRetryingMcpCapabilityClient(McpCapabilityClient delegate) {
        this(delegate, AgentRuntimeTelemetry.NOOP);
    }

    public SafeRetryingMcpCapabilityClient(
            McpCapabilityClient delegate, AgentRuntimeTelemetry telemetry) {
        this(
                delegate,
                DEFAULT_MAXIMUM_ATTEMPTS,
                DEFAULT_INITIAL_BACKOFF,
                Thread::sleep,
                telemetry);
    }

    SafeRetryingMcpCapabilityClient(
            McpCapabilityClient delegate,
            int maximumAttempts,
            Duration initialBackoff,
            RetryDelay delay) {
        this(delegate, maximumAttempts, initialBackoff, delay, AgentRuntimeTelemetry.NOOP);
    }

    SafeRetryingMcpCapabilityClient(
            McpCapabilityClient delegate,
            int maximumAttempts,
            Duration initialBackoff,
            RetryDelay delay,
            AgentRuntimeTelemetry telemetry) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        if (maximumAttempts < 1 || maximumAttempts > DEFAULT_MAXIMUM_ATTEMPTS) {
            throw new IllegalArgumentException("maximumAttempts must be between 1 and 3");
        }
        this.maximumAttempts = maximumAttempts;
        this.initialBackoff = Objects.requireNonNull(initialBackoff, "initialBackoff must not be null");
        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initialBackoff must be positive");
        }
        this.delay = Objects.requireNonNull(delay, "delay must not be null");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry must not be null");
    }

    @Override
    public Map<String, Object> call(String toolName, Map<String, Object> arguments) {
        Objects.requireNonNull(toolName, "toolName must not be null");
        Objects.requireNonNull(arguments, "arguments must not be null");
        for (int attempt = 1; ; attempt++) {
            try {
                return delegate.call(toolName, arguments);
            } catch (McpClientException failure) {
                if (!shouldRetry(toolName, failure.code(), attempt)) {
                    throw failure;
                }
                int currentAttempt = attempt;
                Duration backoff = initialBackoff.multipliedBy(attempt);
                emit(() -> telemetry.retryScheduled(
                        toolName, failure.code(), currentAttempt, backoff));
                pause(backoff);
            }
        }
    }

    public static Set<String> retryableTools() {
        return RETRYABLE_TOOLS;
    }

    public static Set<String> retryableFailureCodes() {
        return RETRYABLE_FAILURES;
    }

    private boolean shouldRetry(String toolName, String failureCode, int attempt) {
        return attempt < maximumAttempts
                && RETRYABLE_TOOLS.contains(toolName)
                && RETRYABLE_FAILURES.contains(failureCode);
    }

    private void pause(Duration backoff) {
        try {
            delay.pause(backoff);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new McpClientException(
                    "MCP_RETRY_INTERRUPTED", "MCP retry backoff was interrupted", exception);
        }
    }

    private static void emit(Runnable signal) {
        try {
            signal.run();
        } catch (RuntimeException ignored) {
            // Telemetry is deliberately unable to alter retry or business behavior.
        }
    }

    @FunctionalInterface
    interface RetryDelay {
        void pause(Duration duration) throws InterruptedException;
    }
}
