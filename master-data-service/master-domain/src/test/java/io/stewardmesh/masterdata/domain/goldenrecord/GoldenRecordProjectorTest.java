package io.stewardmesh.masterdata.domain.goldenrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchFeature;
import io.stewardmesh.masterdata.domain.identity.MatchFeatureCode;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.identity.MatchSignal;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoldenRecordProjectorTest {

    private static final Instant PROJECTED_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final MatchRulesetId MATCH_RULESET = new MatchRulesetId("supplier-match-v1");
    private static final GoldenRecordVersion VERSION = new GoldenRecordVersion(1);
    private static final SupplierPartyId PARTY_ID = new SupplierPartyId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final SupplierAddressId ADDRESS_A = new SupplierAddressId(
            UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final SupplierAddressId ADDRESS_B = new SupplierAddressId(
            UUID.fromString("20000000-0000-0000-0000-000000000002"));
    private static final SupplierSiteId SITE_A = new SupplierSiteId(
            UUID.fromString("30000000-0000-0000-0000-000000000001"));
    private static final SupplierSiteId SITE_B = new SupplierSiteId(
            UUID.fromString("30000000-0000-0000-0000-000000000002"));

    private final GoldenRecordProjector projector = new GoldenRecordProjector();

    @Test
    void sourcePriorityWinsBeforeRecency() {
        var trusted = assertion(
                "trusted",
                1,
                PROJECTED_AT.minusSeconds(3_600),
                900,
                ADDRESS_A,
                SITE_A,
                Map.of(
                        GoldenAttributeName.LEGAL_NAME, "SYNTHETIC TRUSTED LLC",
                        GoldenAttributeName.INN, "9902000005"));
        var recent = assertion(
                "recent",
                1,
                PROJECTED_AT.minusSeconds(60),
                100,
                ADDRESS_A,
                SITE_A,
                Map.of(
                        GoldenAttributeName.LEGAL_NAME, "SYNTHETIC RECENT LLC",
                        GoldenAttributeName.INN, "9902000005"));

        var party = projector.projectParty(PARTY_ID, VERSION, PROJECTED_AT, List.of(recent, trusted));

        assertEquals("SYNTHETIC TRUSTED LLC", party.attributes().get(GoldenAttributeName.LEGAL_NAME).value());
        assertEquals(
                SurvivorshipRule.TRUSTED_SOURCE,
                party.attributes().get(GoldenAttributeName.LEGAL_NAME).provenance().rule());
    }

    @Test
    void recencyWinsWithinTheSamePriority() {
        var older = assertion(
                "older",
                1,
                PROJECTED_AT.minusSeconds(3_600),
                500,
                ADDRESS_A,
                SITE_A,
                partyValues("SYNTHETIC OLD LLC"));
        var newer = assertion(
                "newer",
                1,
                PROJECTED_AT.minusSeconds(60),
                500,
                ADDRESS_A,
                SITE_A,
                partyValues("SYNTHETIC NEW LLC"));

        var party = projector.projectParty(PARTY_ID, VERSION, PROJECTED_AT, List.of(older, newer));

        var legalName = party.attributes().get(GoldenAttributeName.LEGAL_NAME);
        assertEquals("SYNTHETIC NEW LLC", legalName.value());
        assertEquals(SurvivorshipRule.MOST_RECENT_VERIFIED, legalName.provenance().rule());
    }

    @Test
    void completenessBreaksEqualPriorityAndRecency() {
        var sparse = assertion(
                "sparse",
                1,
                PROJECTED_AT.minusSeconds(60),
                500,
                ADDRESS_A,
                SITE_A,
                partyValues("SYNTHETIC SPARSE LLC"));
        var complete = assertion(
                "complete",
                1,
                PROJECTED_AT.minusSeconds(60),
                500,
                ADDRESS_A,
                SITE_A,
                Map.of(
                        GoldenAttributeName.LEGAL_NAME, "SYNTHETIC COMPLETE LLC",
                        GoldenAttributeName.INN, "9902000005",
                        GoldenAttributeName.OGRN, "1027700132195"));

        var party = projector.projectParty(PARTY_ID, VERSION, PROJECTED_AT, List.of(sparse, complete));

        var legalName = party.attributes().get(GoldenAttributeName.LEGAL_NAME);
        assertEquals("SYNTHETIC COMPLETE LLC", legalName.value());
        assertEquals(SurvivorshipRule.MOST_COMPLETE, legalName.provenance().rule());
    }

    @Test
    void missingTrustedValueFallsBackWithCompleteProvenance() {
        var trustedWithoutOgrn = assertion(
                "trusted",
                1,
                PROJECTED_AT.minusSeconds(60),
                900,
                ADDRESS_A,
                SITE_A,
                partyValues("SYNTHETIC TRUSTED LLC"));
        var fallback = assertion(
                "fallback",
                2,
                PROJECTED_AT.minusSeconds(120),
                100,
                ADDRESS_A,
                SITE_A,
                Map.of(
                        GoldenAttributeName.LEGAL_NAME, "SYNTHETIC FALLBACK LLC",
                        GoldenAttributeName.INN, "9902000005",
                        GoldenAttributeName.OGRN, "1027700132195"));

        var party = projector.projectParty(
                PARTY_ID, VERSION, PROJECTED_AT, List.of(fallback, trustedWithoutOgrn));

        var ogrn = party.attributes().get(GoldenAttributeName.OGRN);
        assertEquals("1027700132195", ogrn.value());
        assertEquals(SurvivorshipRule.SOURCE_PRIORITY_FALLBACK, ogrn.provenance().rule());
        assertEquals(fallback.sourceRecord(), ogrn.provenance().sourceRecord());
        assertEquals(GoldenRecordProjector.RULESET_ID, ogrn.provenance().rulesetId());
        assertEquals(PROJECTED_AT, ogrn.provenance().decidedAt());
    }

    @Test
    void recalculationIsDeterministicAcrossInputOrder() {
        var first = assertion(
                "A",
                1,
                PROJECTED_AT.minusSeconds(60),
                500,
                ADDRESS_A,
                SITE_A,
                partyValues("SYNTHETIC A LLC"));
        var second = assertion(
                "B",
                1,
                PROJECTED_AT.minusSeconds(60),
                500,
                ADDRESS_A,
                SITE_A,
                partyValues("SYNTHETIC B LLC"));

        var forward = projector.projectParty(PARTY_ID, VERSION, PROJECTED_AT, List.of(first, second));
        var reverse = projector.projectParty(PARTY_ID, VERSION, PROJECTED_AT, List.of(second, first));

        assertEquals(forward, reverse);
        assertEquals(
                SurvivorshipRule.DETERMINISTIC_TIE_BREAK,
                forward.attributes().get(GoldenAttributeName.LEGAL_NAME).provenance().rule());
    }

    @Test
    void keepsDifferentSitesSeparateForTheSameParty() {
        var siteA = assertion(
                "site-a",
                1,
                PROJECTED_AT.minusSeconds(60),
                500,
                ADDRESS_A,
                SITE_A,
                siteValues("990201001", "SITE-A"));
        var siteB = assertion(
                "site-b",
                1,
                PROJECTED_AT.minusSeconds(60),
                500,
                ADDRESS_B,
                SITE_B,
                siteValues("990202002", "SITE-B"));

        var projectionA = projector.projectSite(
                SITE_A, PARTY_ID, ADDRESS_A, VERSION, PROJECTED_AT, List.of(siteB, siteA));
        var projectionB = projector.projectSite(
                SITE_B, PARTY_ID, ADDRESS_B, VERSION, PROJECTED_AT, List.of(siteA, siteB));

        assertEquals("990201001", projectionA.attributes().get(GoldenAttributeName.KPP).value());
        assertEquals("990202002", projectionB.attributes().get(GoldenAttributeName.KPP).value());
        assertNotEquals(projectionA.id(), projectionB.id());
        assertEquals(List.of(siteA.association().id()), projectionA.sourceAssociations());
        assertEquals(List.of(siteB.association().id()), projectionB.sourceAssociations());
    }

    @Test
    void projectsPartyAddressAndSiteAsDistinctVersionedModels() {
        var assertion = assertion(
                "complete-row",
                1,
                PROJECTED_AT.minusSeconds(60),
                500,
                ADDRESS_A,
                SITE_A,
                Map.ofEntries(
                        Map.entry(GoldenAttributeName.LEGAL_NAME, "SYNTHETIC COMPLETE LLC"),
                        Map.entry(GoldenAttributeName.INN, "9902000005"),
                        Map.entry(GoldenAttributeName.COUNTRY_CODE, "RU"),
                        Map.entry(GoldenAttributeName.CITY, "TEST CITY"),
                        Map.entry(GoldenAttributeName.ADDRESS_LINE, "1 TEST STREET"),
                        Map.entry(GoldenAttributeName.KPP, "990201001"),
                        Map.entry(GoldenAttributeName.PROCUREMENT_BUSINESS_UNIT_CODE, "BU-TEST")));

        var party = projector.projectParty(PARTY_ID, VERSION, PROJECTED_AT, List.of(assertion));
        var address = projector.projectAddress(
                ADDRESS_A, PARTY_ID, VERSION, PROJECTED_AT, List.of(assertion));
        var site = projector.projectSite(
                SITE_A, PARTY_ID, ADDRESS_A, VERSION, PROJECTED_AT, List.of(assertion));

        assertEquals(PARTY_ID, party.id());
        assertEquals(ADDRESS_A, address.id());
        assertEquals(PARTY_ID, address.partyId());
        assertEquals(SITE_A, site.id());
        assertEquals(ADDRESS_A, site.addressId());
        assertEquals(VERSION, site.version());
        assertEquals(GoldenRecordProjector.RULESET_ID, site.rulesetId());
        assertEquals(PROJECTED_AT, site.projectedAt());
        assertEquals("TEST CITY", address.attributes().get(GoldenAttributeName.CITY).value());
        assertEquals(
                "BU-TEST",
                site.attributes()
                        .get(GoldenAttributeName.PROCUREMENT_BUSINESS_UNIT_CODE)
                        .value());
    }

    @Test
    void excludesUnlinkedSourcesFromRecalculation() {
        var active = assertion(
                "active",
                1,
                PROJECTED_AT.minusSeconds(120),
                100,
                ADDRESS_A,
                SITE_A,
                partyValues("SYNTHETIC ACTIVE LLC"));
        var associated = assertion(
                "removed",
                1,
                PROJECTED_AT.minusSeconds(60),
                900,
                ADDRESS_A,
                SITE_A,
                partyValues("SYNTHETIC REMOVED LLC"));
        var removed = new GoldenSourceAssertion(
                associated.sourceRecord(),
                associated.ingestedAt(),
                associated.priority(),
                associated.association().unlink(PROJECTED_AT.minusSeconds(30)),
                associated.canonicalValues());

        var party = projector.projectParty(PARTY_ID, VERSION, PROJECTED_AT, List.of(active, removed));

        assertEquals("SYNTHETIC ACTIVE LLC", party.attributes().get(GoldenAttributeName.LEGAL_NAME).value());
        assertThrows(
                IllegalArgumentException.class,
                () -> projector.projectSite(
                        SITE_B, PARTY_ID, ADDRESS_B, VERSION, PROJECTED_AT, List.of(active, removed)));
    }

    private static Map<GoldenAttributeName, String> partyValues(String legalName) {
        return Map.of(
                GoldenAttributeName.LEGAL_NAME, legalName,
                GoldenAttributeName.INN, "9902000005");
    }

    private static Map<GoldenAttributeName, String> siteValues(String kpp, String siteCode) {
        return Map.of(
                GoldenAttributeName.KPP, kpp,
                GoldenAttributeName.SITE_CODE, siteCode);
    }

    private static GoldenSourceAssertion assertion(
            String sourceRecordId,
            long sourceVersion,
            Instant ingestedAt,
            int priority,
            SupplierAddressId addressId,
            SupplierSiteId siteId,
            Map<GoldenAttributeName, String> values) {
        var source = new SourceRecordIdentity(
                new SourceSystemRef("synthetic-erp"), sourceRecordId, sourceVersion);
        var associationId = new SourceAssociationId(UUID.nameUUIDFromBytes(
                (sourceRecordId + sourceVersion).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var association = new SourceAssociation(
                associationId,
                source,
                PARTY_ID,
                Optional.of(addressId),
                Optional.of(siteId),
                decision(MatchEntityType.PARTY, PARTY_ID.value()),
                Optional.of(decision(MatchEntityType.SITE, siteId.value())),
                ingestedAt,
                Optional.empty());
        return new GoldenSourceAssertion(
                source, ingestedAt, new SourcePriority(priority), association, values);
    }

    private static MatchDecision decision(MatchEntityType type, UUID candidateId) {
        return new MatchDecision(
                type,
                candidateId,
                MatchOutcome.AUTO_LINK,
                10_000,
                MATCH_RULESET,
                false,
                List.of(new MatchFeature(
                        MatchFeatureCode.INN_EXACT, MatchSignal.MATCH, 10_000)));
    }
}
