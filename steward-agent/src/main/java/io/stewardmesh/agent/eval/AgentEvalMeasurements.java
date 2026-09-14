package io.stewardmesh.agent.eval;

/** Measurements supplied by the execution harness around one reference-agent run. */
public record AgentEvalMeasurements(
        boolean escalated,
        boolean unsafeAction,
        boolean recoverySucceeded,
        int sideEffects,
        long latencyMillis,
        long costMicrounits) {

    public AgentEvalMeasurements {
        if (sideEffects < 0 || latencyMillis < 0 || costMicrounits < 0) {
            throw new IllegalArgumentException("eval measurements must not be negative");
        }
    }
}
