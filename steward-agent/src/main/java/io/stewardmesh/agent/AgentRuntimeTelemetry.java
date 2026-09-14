package io.stewardmesh.agent;

import java.time.Duration;

/** Low-cardinality operational signals emitted outside the governed workflow state. */
public interface AgentRuntimeTelemetry {

    AgentRuntimeTelemetry NOOP = new AgentRuntimeTelemetry() {};

    default void modelDecision(
            AgentPhase phase, String outcome, Duration latency, ModelTokenUsage tokenUsage) {}

    default void toolCall(AgentPhase phase, String toolName, String outcome, Duration latency) {}

    default void retryScheduled(
            String toolName, String failureCode, int attempt, Duration backoff) {}

    default void runCompleted(int toolCalls, Duration latency) {}

    default void runFailed(String failureCode, int toolCalls, Duration latency) {}
}
