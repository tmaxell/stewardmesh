package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.application.identity.MatchEvaluationSummary;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Optional;

@FunctionalInterface
public interface LoadMatchEvaluationSummary {

    Optional<MatchEvaluationSummary> find(
            SourceRecordIdentity sourceRecordIdentity, MatchRulesetId rulesetId);
}
