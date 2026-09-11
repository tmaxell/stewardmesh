package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation;
import java.util.Objects;

final class MatchEvaluationReads {

    private MatchEvaluationReads() {}

    static MatchEvaluation require(LoadMatchEvaluation evaluations, IdentityResolutionKey key) {
        return evaluations.find(Objects.requireNonNull(key, "key must not be null"))
                .orElseThrow(IdentityResolutionNotFoundException::new);
    }
}
