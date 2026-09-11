package io.stewardmesh.masterdata.domain.goldenrecord;

import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Auditable evidence for either an automatic link or deliberate new-entity creation. */
public record AssociationEvidence(
        AssociationKind kind, MatchRulesetId rulesetId, Optional<MatchDecision> matchDecision) {

    public AssociationEvidence {
        Objects.requireNonNull(kind, "kind must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        matchDecision = Objects.requireNonNull(matchDecision, "matchDecision must not be null");
        if (kind == AssociationKind.NEW_ENTITY && matchDecision.isPresent()) {
            throw new IllegalArgumentException("new-entity evidence cannot contain a match decision");
        }
        matchDecision.ifPresent(decision -> {
            if (kind != AssociationKind.AUTO_LINK
                    || decision.outcome() != MatchOutcome.AUTO_LINK
                    || decision.hardConflict()
                    || !decision.rulesetId().equals(rulesetId)) {
                throw new IllegalArgumentException("auto-link evidence requires a conflict-free decision");
            }
        });
        if (kind == AssociationKind.AUTO_LINK && matchDecision.isEmpty()) {
            throw new IllegalArgumentException("auto-link evidence requires a match decision");
        }
    }

    public static AssociationEvidence autoLinked(MatchDecision decision) {
        Objects.requireNonNull(decision, "decision must not be null");
        return new AssociationEvidence(
                AssociationKind.AUTO_LINK, decision.rulesetId(), Optional.of(decision));
    }

    public static AssociationEvidence newEntity(MatchRulesetId rulesetId) {
        return new AssociationEvidence(AssociationKind.NEW_ENTITY, rulesetId, Optional.empty());
    }

    void requireTarget(MatchEntityType entityType, UUID targetId) {
        matchDecision.ifPresent(decision -> {
            if (decision.entityType() != entityType || !decision.candidateId().equals(targetId)) {
                throw new IllegalArgumentException(
                        "match evidence does not identify the associated target");
            }
        });
    }
}
