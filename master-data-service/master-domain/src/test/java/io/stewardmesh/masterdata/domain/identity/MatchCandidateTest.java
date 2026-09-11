package io.stewardmesh.masterdata.domain.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MatchCandidateTest {

    private static final SupplierPartyId PARTY_ID =
            new SupplierPartyId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb101"));

    @Test
    void retainsPartyAndSiteIdentityAsDifferentCandidateKinds() {
        var party = new PartyCandidate(PARTY_ID, 8_000, List.of(feature(MatchFeatureCode.INN_EXACT)));
        var site = new SiteCandidate(
                new SupplierSiteId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb102")),
                PARTY_ID,
                7_000,
                List.of(feature(MatchFeatureCode.KPP_EXACT)));

        assertEquals(MatchEntityType.PARTY, party.entityType());
        assertEquals(PARTY_ID.value(), party.candidateId());
        assertEquals(MatchEntityType.SITE, site.entityType());
        assertEquals(PARTY_ID, site.partyId());
    }

    @Test
    void ordersFeaturesByStableCodeAndDoesNotExposeMutableInput() {
        var mutable = new ArrayList<>(List.of(
                feature(MatchFeatureCode.OGRN_EXACT),
                feature(MatchFeatureCode.INN_EXACT)));

        var candidate = new PartyCandidate(PARTY_ID, 9_000, mutable);
        mutable.clear();

        assertEquals(
                List.of(MatchFeatureCode.INN_EXACT, MatchFeatureCode.OGRN_EXACT),
                candidate.features().stream().map(MatchFeature::code).toList());
        assertThrows(
                UnsupportedOperationException.class,
                () -> candidate.features().add(feature(MatchFeatureCode.KPP_EXACT)));
    }

    @Test
    void rejectsDuplicateFeaturesAndOutOfRangeScores() {
        var duplicate = feature(MatchFeatureCode.INN_EXACT);

        assertThrows(
                IllegalArgumentException.class,
                () -> new PartyCandidate(PARTY_ID, 8_000, List.of(duplicate, duplicate)));
        assertThrows(
                IllegalArgumentException.class,
                () -> new PartyCandidate(PARTY_ID, 10_001, List.of(duplicate)));
    }

    @Test
    void rejectsUnsafeFeatureContributions() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchFeature(MatchFeatureCode.INN_EXACT, MatchSignal.MISSING, 1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MatchFeature(
                        MatchFeatureCode.AUTHORITATIVE_IDENTIFIER_CONFLICT,
                        MatchSignal.CONFLICT,
                        1));
    }

    private static MatchFeature feature(MatchFeatureCode code) {
        return new MatchFeature(code, MatchSignal.MATCH, 1_000);
    }
}
