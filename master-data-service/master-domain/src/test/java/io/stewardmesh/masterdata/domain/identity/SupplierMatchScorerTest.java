package io.stewardmesh.masterdata.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SupplierMatchScorerTest {

    private static final SupplierPartyId PARTY_ID =
            new SupplierPartyId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb201"));
    private static final SupplierSiteId SITE_ID =
            new SupplierSiteId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb301"));
    private final SupplierMatchScorer scorer = new SupplierMatchScorer();

    @Test
    void autoLinksAnExactPartyWithVersionedEvidence() {
        PartyCandidate candidate = scorer.scoreParty(
                source(),
                new PartyMatchProfile(PARTY_ID, "9902000005", "1027700132195", "SYNTHETIC ALPHA LLC"));

        MatchDecision decision = scorer.decide(candidate);

        assertEquals(10_000, decision.scoreBasisPoints());
        assertEquals(MatchOutcome.AUTO_LINK, decision.outcome());
        assertEquals(SupplierMatchScorer.RULESET.id(), decision.rulesetId());
        assertFalse(decision.hardConflict());
    }

    @Test
    void routesAuthoritativePartyConflictToReviewEvenWithSimilarName() {
        PartyCandidate candidate = scorer.scoreParty(
                source(),
                new PartyMatchProfile(PARTY_ID, "7707083893", "1027700132195", "SYNTHETIC ALPHA LLC"));

        MatchDecision decision = scorer.decide(candidate);

        assertTrue(decision.hardConflict());
        assertEquals(MatchOutcome.REVIEW, decision.outcome());
        assertEquals(
                MatchSignal.CONFLICT,
                decision.features().stream()
                        .filter(feature -> feature.code() == MatchFeatureCode.INN_EXACT)
                        .findFirst()
                        .orElseThrow()
                        .signal());
    }

    @Test
    void computesDeterministicPartialLegalNameSimilarity() {
        PartyCandidate first = scorer.scoreParty(
                source(),
                new PartyMatchProfile(PARTY_ID, "9902000005", null, "SYNTHETIC ALPHA GROUP"));
        PartyCandidate second = scorer.scoreParty(
                source(),
                new PartyMatchProfile(PARTY_ID, "9902000005", null, "SYNTHETIC ALPHA GROUP"));

        assertEquals(first, second);
        assertEquals(6_000, first.scoreBasisPoints());
        assertEquals(MatchOutcome.REVIEW, scorer.decide(first).outcome());
    }

    @Test
    void separatesSameInnSitesByKppAndAddressContext() {
        SiteCandidate candidate = scorer.scoreSite(
                source(),
                new SiteMatchProfile(
                        SITE_ID,
                        PARTY_ID,
                        "9902000005",
                        "990299999",
                        "SITE-A",
                        "RU",
                        "TEST CITY",
                        "TEST ADDRESS"));

        MatchDecision decision = scorer.decide(candidate);

        assertEquals(7_500, decision.scoreBasisPoints());
        assertTrue(decision.hardConflict());
        assertEquals(MatchOutcome.REVIEW, decision.outcome());
    }

    @Test
    void autoLinksAnExactSiteWithoutRequiringSourceSiteCode() {
        SupplierMatchInput source = new SupplierMatchInput(
                "9902000005",
                null,
                "SYNTHETIC ALPHA LLC",
                "990201001",
                null,
                "RU",
                "TEST CITY",
                "TEST ADDRESS");
        SiteCandidate candidate = scorer.scoreSite(
                source,
                new SiteMatchProfile(
                        SITE_ID,
                        PARTY_ID,
                        "9902000005",
                        "990201001",
                        null,
                        "RU",
                        "TEST CITY",
                        "TEST ADDRESS"));

        assertEquals(9_000, candidate.scoreBasisPoints());
        assertEquals(MatchOutcome.AUTO_LINK, scorer.decide(candidate).outcome());
    }

    private static SupplierMatchInput source() {
        return new SupplierMatchInput(
                "9902000005",
                "1027700132195",
                "SYNTHETIC ALPHA LLC",
                "990201001",
                "SITE-A",
                "RU",
                "TEST CITY",
                "TEST ADDRESS");
    }
}
