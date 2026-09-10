package io.stewardmesh.masterdata.application.identity;

import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Persistable, reproducible decision set for one immutable source assertion. */
public record MatchEvaluation(
        SourceRecordIdentity sourceRecordIdentity,
        MatchRulesetId rulesetId,
        Instant evaluatedAt,
        List<MatchDecision> partyDecisions,
        List<MatchDecision> siteDecisions) {

    public MatchEvaluation {
        Objects.requireNonNull(sourceRecordIdentity, "sourceRecordIdentity must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        Objects.requireNonNull(evaluatedAt, "evaluatedAt must not be null");
        partyDecisions = decisions(partyDecisions, MatchEntityType.PARTY, rulesetId);
        siteDecisions = decisions(siteDecisions, MatchEntityType.SITE, rulesetId);
    }

    private static List<MatchDecision> decisions(
            List<MatchDecision> decisions, MatchEntityType entityType, MatchRulesetId rulesetId) {
        List<MatchDecision> stable = Objects.requireNonNull(decisions, "decisions must not be null").stream()
                .map(decision -> Objects.requireNonNull(decision, "decision must not be null"))
                .sorted(Comparator.comparing(MatchDecision::candidateId))
                .toList();
        if (stable.stream().anyMatch(decision -> decision.entityType() != entityType)) {
            throw new IllegalArgumentException("match evaluation mixes entity types");
        }
        if (stable.stream().anyMatch(decision -> !decision.rulesetId().equals(rulesetId))) {
            throw new IllegalArgumentException("match evaluation mixes rulesets");
        }
        var identifiers = new HashSet<UUID>();
        if (stable.stream().map(MatchDecision::candidateId).anyMatch(id -> !identifiers.add(id))) {
            throw new IllegalArgumentException("match evaluation contains duplicate candidates");
        }
        return stable;
    }
}
