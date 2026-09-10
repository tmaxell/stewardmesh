package io.stewardmesh.masterdata.application.goldenrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttribute;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeProvenance;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordProjector;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociation;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierAddress;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierParty;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierSite;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRule;
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

class GoldenRecordProjectionTest {

    private static final Instant PROJECTED_AT = Instant.parse("2026-09-11T10:00:00Z");
    private static final MatchRulesetId MATCH_RULESET = new MatchRulesetId("supplier-match-v1");

    @Test
    void acceptsOneConsistentPartyGraph() {
        var graph = graph(UUID.randomUUID());

        var projection = new GoldenRecordProjection(
                graph.party(), List.of(graph.address()), List.of(graph.site()), List.of(graph.association()));

        assertEquals(graph.party(), projection.party());
        assertEquals(List.of(graph.address()), projection.addresses());
        assertEquals(List.of(graph.site()), projection.sites());
        assertEquals(List.of(graph.association()), projection.sourceAssociations());
    }

    @Test
    void rejectsDuplicateOrMissingAssociationEvidence() {
        var graph = graph(UUID.randomUUID());

        assertThrows(
                IllegalArgumentException.class,
                () -> new GoldenRecordProjection(
                        graph.party(),
                        List.of(graph.address()),
                        List.of(graph.site()),
                        List.of(graph.association(), graph.association())));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GoldenRecordProjection(
                        graph.party(), List.of(graph.address()), List.of(graph.site()), List.of()));
    }

    @Test
    void rejectsEntitiesAndAssociationsFromAnotherParty() {
        var graph = graph(UUID.randomUUID());
        var other = graph(UUID.randomUUID());

        assertThrows(
                IllegalArgumentException.class,
                () -> new GoldenRecordProjection(
                        graph.party(), List.of(other.address()), List.of(), List.of(graph.association())));
        assertThrows(
                IllegalArgumentException.class,
                () -> new GoldenRecordProjection(
                        graph.party(), List.of(), List.of(), List.of(other.association())));
    }

    @Test
    void writeFailureRetainsItsPersistenceCause() {
        var cause = new IllegalStateException("synthetic persistence failure");

        var failure = new GoldenRecordWriteException("golden write failed", cause);

        assertEquals(cause, failure.getCause());
    }

    private static Graph graph(UUID seed) {
        var partyId = new SupplierPartyId(seed);
        var addressId = new SupplierAddressId(UUID.nameUUIDFromBytes((seed + "address").getBytes()));
        var siteId = new SupplierSiteId(UUID.nameUUIDFromBytes((seed + "site").getBytes()));
        var source = new SourceRecordIdentity(new SourceSystemRef("synthetic-test"), seed.toString(), 1);
        var associationId = new SourceAssociationId(
                UUID.nameUUIDFromBytes((seed + "association").getBytes()));
        var association = new SourceAssociation(
                associationId,
                source,
                partyId,
                Optional.of(addressId),
                Optional.of(siteId),
                decision(MatchEntityType.PARTY, partyId.value()),
                Optional.of(decision(MatchEntityType.SITE, siteId.value())),
                PROJECTED_AT.minusSeconds(60),
                Optional.empty());
        var provenance = new GoldenAttributeProvenance(
                source,
                associationId,
                SurvivorshipRule.TRUSTED_SOURCE,
                GoldenRecordProjector.RULESET_ID,
                PROJECTED_AT);
        var party = new SupplierParty(
                partyId,
                new GoldenRecordVersion(1),
                GoldenRecordProjector.RULESET_ID,
                PROJECTED_AT,
                Map.of(
                        GoldenAttributeName.LEGAL_NAME,
                        new GoldenAttribute(GoldenAttributeName.LEGAL_NAME, "SYNTHETIC LLC", provenance),
                        GoldenAttributeName.INN,
                        new GoldenAttribute(GoldenAttributeName.INN, "9902000005", provenance)),
                List.of(associationId));
        var address = new SupplierAddress(
                addressId,
                partyId,
                new GoldenRecordVersion(1),
                GoldenRecordProjector.RULESET_ID,
                PROJECTED_AT,
                Map.of(
                        GoldenAttributeName.COUNTRY_CODE,
                        new GoldenAttribute(GoldenAttributeName.COUNTRY_CODE, "RU", provenance),
                        GoldenAttributeName.CITY,
                        new GoldenAttribute(GoldenAttributeName.CITY, "TEST CITY", provenance),
                        GoldenAttributeName.ADDRESS_LINE,
                        new GoldenAttribute(GoldenAttributeName.ADDRESS_LINE, "TEST STREET", provenance)),
                List.of(associationId));
        var site = new SupplierSite(
                siteId,
                partyId,
                addressId,
                new GoldenRecordVersion(1),
                GoldenRecordProjector.RULESET_ID,
                PROJECTED_AT,
                Map.of(
                        GoldenAttributeName.KPP,
                        new GoldenAttribute(GoldenAttributeName.KPP, "990201001", provenance)),
                List.of(associationId));
        return new Graph(party, address, site, association);
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

    private record Graph(
            SupplierParty party,
            SupplierAddress address,
            SupplierSite site,
            SourceAssociation association) {}
}
