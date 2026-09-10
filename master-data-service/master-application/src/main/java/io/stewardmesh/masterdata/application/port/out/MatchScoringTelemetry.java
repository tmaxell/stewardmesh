package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.identity.MatchEvaluation;
import java.util.function.Supplier;

public interface MatchScoringTelemetry {

    <T> T measure(Supplier<T> operation);

    void record(MatchEvaluation evaluation);
}
