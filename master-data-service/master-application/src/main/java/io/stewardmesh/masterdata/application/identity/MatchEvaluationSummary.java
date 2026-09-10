package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import java.time.Instant;
import java.util.Objects;
import java.util.stream.Stream;

/** Minimal persisted evaluation view required to resume review routing. */
public record MatchEvaluationSummary(
        boolean reviewRequired, boolean hardConflict, Instant evaluatedAt) {

    public MatchEvaluationSummary {
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        if (hardConflict && !reviewRequired) {
            throw new IllegalArgumentException("a hard conflict must require review");
        }
    }

    public static MatchEvaluationSummary from(MatchEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        var decisions = Stream.concat(
                evaluation.partyDecisions().stream(), evaluation.siteDecisions().stream());
        var evidence = decisions.toList();
        boolean conflict = evidence.stream().anyMatch(decision -> decision.hardConflict());
        boolean review = conflict
                || evidence.stream().anyMatch(decision -> decision.outcome() == MatchOutcome.REVIEW);
        return new MatchEvaluationSummary(review, conflict, evaluation.evaluatedAt());
    }
}
