package io.stewardmesh.masterdata.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchRulesetTest {

    private static final SupplierPartyId PARTY_ID =
            new SupplierPartyId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb111"));
    private static final MatchRuleset RULESET = new MatchRuleset(
            new MatchRulesetId("supplier-identity-v1"),
            8_500,
            6_000,
            Set.of(MatchFeatureCode.AUTHORITATIVE_IDENTIFIER_CONFLICT));

    @Test
    void appliesVersionedThresholdsAtTheirExactBoundaries() {
        assertEquals(MatchOutcome.AUTO_LINK, RULESET.decide(candidate(8_500)).outcome());
        assertEquals(MatchOutcome.REVIEW, RULESET.decide(candidate(6_000)).outcome());
        assertEquals(MatchOutcome.NO_MATCH, RULESET.decide(candidate(5_999)).outcome());
    }

    @Test
    void routesAuthoritativeConflictToReviewRegardlessOfScore() {
        var conflict = new MatchFeature(
                MatchFeatureCode.AUTHORITATIVE_IDENTIFIER_CONFLICT,
                MatchSignal.CONFLICT,
                -5_000);

        var decision = RULESET.decide(new PartyCandidate(PARTY_ID, 1_000, List.of(conflict)));

        assertEquals(MatchOutcome.REVIEW, decision.outcome());
        assertTrue(decision.hardConflict());
        assertEquals(RULESET.id(), decision.rulesetId());
        assertEquals(List.of(conflict), decision.features());
    }

    @Test
    void doesNotTreatAnUnconfiguredConflictAsHardPolicyEvidence() {
        var mismatch = new MatchFeature(MatchFeatureCode.KPP_EXACT, MatchSignal.CONFLICT, -1_000);

        var decision = RULESET.decide(new PartyCandidate(PARTY_ID, 9_000, List.of(mismatch)));

        assertEquals(MatchOutcome.AUTO_LINK, decision.outcome());
        assertFalse(decision.hardConflict());
    }

    @Test
    void rejectsInvertedThresholdsAndUnsafeDecisions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchRuleset(
                        new MatchRulesetId("supplier-identity-v1"), 6_000, 8_500, Set.of()));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchDecision(
                        MatchEntityType.PARTY,
                        PARTY_ID.value(),
                        MatchOutcome.AUTO_LINK,
                        9_000,
                        RULESET.id(),
                        true,
                        List.of(new MatchFeature(
                                MatchFeatureCode.AUTHORITATIVE_IDENTIFIER_CONFLICT,
                                MatchSignal.CONFLICT,
                                -1_000))));
    }

    private static PartyCandidate candidate(int score) {
        return new PartyCandidate(
                PARTY_ID,
                score,
                List.of(new MatchFeature(MatchFeatureCode.INN_EXACT, MatchSignal.MATCH, score)));
    }
}
