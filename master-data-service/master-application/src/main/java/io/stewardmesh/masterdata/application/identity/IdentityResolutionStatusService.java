package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.application.port.in.GetIdentityResolutionStatus;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

public final class IdentityResolutionStatusService implements GetIdentityResolutionStatus {

    private final LoadMatchEvaluation evaluations;

    public IdentityResolutionStatusService(LoadMatchEvaluation evaluations) {
        this.evaluations = Objects.requireNonNull(evaluations, "evaluations must not be null");
    }

    @Override
    public IdentityResolutionStatus execute(IdentityResolutionKey key) {
        MatchEvaluation evaluation = MatchEvaluationReads.require(evaluations, key);
        List<MatchDecision> decisions = Stream.concat(
                evaluation.partyDecisions().stream(), evaluation.siteDecisions().stream()).toList();
        return new IdentityResolutionStatus(
                key,
                evaluation.evaluatedAt(),
                evaluation.partyDecisions().size(),
                evaluation.siteDecisions().size(),
                count(decisions, MatchOutcome.AUTO_LINK),
                count(decisions, MatchOutcome.REVIEW),
                count(decisions, MatchOutcome.NO_MATCH),
                decisions.stream().anyMatch(MatchDecision::hardConflict));
    }

    private static int count(List<MatchDecision> decisions, MatchOutcome outcome) {
        return Math.toIntExact(decisions.stream().filter(decision -> decision.outcome() == outcome).count());
    }
}
