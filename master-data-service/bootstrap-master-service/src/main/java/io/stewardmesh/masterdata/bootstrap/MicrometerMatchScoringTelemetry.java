package io.stewardmesh.masterdata.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.stewardmesh.masterdata.application.identity.MatchEvaluation;
import io.stewardmesh.masterdata.application.port.out.MatchScoringTelemetry;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

final class MicrometerMatchScoringTelemetry implements MatchScoringTelemetry {

    private final MeterRegistry registry;

    MicrometerMatchScoringTelemetry(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public <T> T measure(Supplier<T> operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        Timer.Sample sample = Timer.start(registry);
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            registry.counter("stewardmesh.matching.scoring.failures").increment();
            throw exception;
        } finally {
            sample.stop(registry.timer("stewardmesh.matching.scoring.duration"));
        }
    }

    @Override
    public void record(MatchEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        recordCandidateCount(MatchEntityType.PARTY, evaluation.partyDecisions().size());
        recordCandidateCount(MatchEntityType.SITE, evaluation.siteDecisions().size());
        evaluation.partyDecisions().forEach(this::recordDecision);
        evaluation.siteDecisions().forEach(this::recordDecision);
    }

    private void recordCandidateCount(MatchEntityType entityType, int count) {
        registry.summary(
                        "stewardmesh.matching.candidate.count",
                        "entity",
                        tag(entityType.name()))
                .record(count);
    }

    private void recordDecision(MatchDecision decision) {
        registry.counter(
                        "stewardmesh.matching.decisions",
                        "entity",
                        tag(decision.entityType().name()),
                        "outcome",
                        tag(decision.outcome().name()),
                        "hard_conflict",
                        Boolean.toString(decision.hardConflict()))
                .increment();
    }

    private static String tag(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
