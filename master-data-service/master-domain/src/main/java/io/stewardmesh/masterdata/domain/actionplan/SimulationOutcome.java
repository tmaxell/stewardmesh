package io.stewardmesh.masterdata.domain.actionplan;

/** Whether every step of a sealed plan can execute against the state it was simulated on. */
public enum SimulationOutcome {
    EXECUTABLE,
    BLOCKED
}
