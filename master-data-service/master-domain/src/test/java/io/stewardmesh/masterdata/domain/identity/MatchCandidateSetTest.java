package io.stewardmesh.masterdata.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchCandidateSetTest {

    @Test
    void sortsCandidatesByScoreThenStableIdentifier() {
        var laterId = party("018f3f70-79b2-7d6a-bf40-3d52dc2bb122", 8_000);
        var lowerScore = party("018f3f70-79b2-7d6a-bf40-3d52dc2bb120", 7_000);
        var earlierId = party("018f3f70-79b2-7d6a-bf40-3d52dc2bb121", 8_000);

        var candidates = new MatchCandidateSet(
                MatchEntityType.PARTY, 3, false, List.of(laterId, lowerScore, earlierId));

        assertEquals(
                List.of(earlierId, laterId, lowerScore),
                candidates.candidates());
    }

    @Test
    void rejectsOverLimitDuplicateAndMixedCandidateCollections() {
        var party = party("018f3f70-79b2-7d6a-bf40-3d52dc2bb123", 8_000);
        var site = new SiteCandidate(
                new SupplierSiteId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb124")),
                party.partyId(),
                8_000,
                List.of(feature()));

        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchCandidateSet(MatchEntityType.PARTY, 1, false, List.of(party, party)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchCandidateSet(MatchEntityType.PARTY, 2, false, List.of(party, site)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchCandidateSet(MatchEntityType.PARTY, 2, true, List.of(party)));
    }

    private static PartyCandidate party(String id, int score) {
        return new PartyCandidate(new SupplierPartyId(UUID.fromString(id)), score, List.of(feature()));
    }

    private static MatchFeature feature() {
        return new MatchFeature(MatchFeatureCode.INN_EXACT, MatchSignal.MATCH, 1_000);
    }
}
