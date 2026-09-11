package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.application.port.in.ListIdentityResolutionCandidates;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import java.util.List;
import java.util.Objects;

public final class IdentityResolutionCandidatesService implements ListIdentityResolutionCandidates {

    private final LoadMatchEvaluation evaluations;

    public IdentityResolutionCandidatesService(LoadMatchEvaluation evaluations) {
        this.evaluations = Objects.requireNonNull(evaluations, "evaluations must not be null");
    }

    @Override
    public IdentityResolutionCandidatePage execute(IdentityResolutionCandidateQuery query) {
        MatchEvaluation evaluation = MatchEvaluationReads.require(evaluations, query.key());
        List<MatchDecision> candidates = query.entityType() == MatchEntityType.PARTY
                ? evaluation.partyDecisions() : evaluation.siteDecisions();
        int from = Math.min(Math.multiplyExact(query.page(), query.size()), candidates.size());
        int to = Math.min(from + query.size(), candidates.size());
        return new IdentityResolutionCandidatePage(
                query.key(), query.entityType(), query.page(), query.size(), candidates.size(),
                candidates.subList(from, to));
    }
}
