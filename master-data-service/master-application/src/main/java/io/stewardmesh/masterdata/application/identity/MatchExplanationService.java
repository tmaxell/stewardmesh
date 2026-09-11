package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.application.port.in.GetMatchExplanation;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import java.util.List;
import java.util.Objects;

public final class MatchExplanationService implements GetMatchExplanation {

    private final LoadMatchEvaluation evaluations;

    public MatchExplanationService(LoadMatchEvaluation evaluations) {
        this.evaluations = Objects.requireNonNull(evaluations, "evaluations must not be null");
    }

    @Override
    public MatchDecision execute(MatchExplanationQuery query) {
        MatchEvaluation evaluation = MatchEvaluationReads.require(evaluations, query.key());
        List<MatchDecision> candidates = query.entityType() == MatchEntityType.PARTY
                ? evaluation.partyDecisions() : evaluation.siteDecisions();
        return candidates.stream()
                .filter(candidate -> candidate.candidateId().equals(query.candidateId()))
                .findFirst()
                .orElseThrow(MatchCandidateNotFoundException::new);
    }
}
