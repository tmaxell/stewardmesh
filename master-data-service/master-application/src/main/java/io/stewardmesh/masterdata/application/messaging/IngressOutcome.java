package io.stewardmesh.masterdata.application.messaging;

public enum IngressOutcome {
    ACCEPTED,
    DUPLICATE,
    LOOP_SUPPRESSED,
    REJECTED
}
