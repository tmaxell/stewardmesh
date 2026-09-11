package io.stewardmesh.masterdata.domain.goldenrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SourceAssociationTest {

    private static final Instant LINKED_AT = Instant.parse("2026-09-11T08:00:00Z");
    private static final MatchRulesetId MATCH_RULESET = new MatchRulesetId("supplier-match-v1");
    private static final SourceRecordIdentity SOURCE =
            new SourceRecordIdentity(new SourceSystemRef("synthetic-erp"), "SUP-1", 1);
    private static final SupplierPartyId PARTY_ID = new SupplierPartyId(
            UUID.fromString("10000000-0000-0000-0000-000000000001"));
    private static final SupplierAddressId ADDRESS_ID = new SupplierAddressId(
            UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final SupplierSiteId SITE_ID = new SupplierSiteId(
            UUID.fromString("30000000-0000-0000-0000-000000000001"));

    @Test
    void retainsEvidenceWhenAssociationIsUnlinked() {
        var association = siteAssociation(partyDecision(MatchOutcome.AUTO_LINK, false));

        var unlinked = association.unlink(LINKED_AT.plusSeconds(60));

        assertFalse(unlinked.active());
        assertEquals(association.id(), unlinked.id());
        assertEquals(association.sourceRecord(), unlinked.sourceRecord());
        assertEquals(association.partyDecision(), unlinked.partyDecision());
        assertTrue(unlinked.unlinkedAt().isPresent());
        assertThrows(IllegalStateException.class, () -> unlinked.unlink(LINKED_AT.plusSeconds(120)));
    }

    @Test
    void rejectsHardConflictAndReviewAssociations() {
        var conflict = partyDecision(MatchOutcome.REVIEW, true);

        assertThrows(IllegalArgumentException.class, () -> siteAssociation(conflict));
        assertThrows(
                IllegalArgumentException.class,
                () -> siteAssociation(partyDecision(MatchOutcome.REVIEW, false)));
    }

    @Test
    void requiresSiteEvidenceToMatchTheExactSite() {
        var otherSite = new SupplierSiteId(
                UUID.fromString("30000000-0000-0000-0000-000000000002"));

        assertThrows(
                IllegalArgumentException.class,
                () -> new SourceAssociation(
                        new SourceAssociationId(UUID.fromString("40000000-0000-0000-0000-000000000001")),
                        SOURCE,
                        PARTY_ID,
                        Optional.of(ADDRESS_ID),
                        Optional.of(SITE_ID),
                        partyDecision(MatchOutcome.AUTO_LINK, false),
                        Optional.of(decision(MatchEntityType.SITE, otherSite.value(), MatchOutcome.AUTO_LINK, false)),
                        LINKED_AT,
                        Optional.empty()));
    }

    @Test
    void recordsNewEntityCreationWithoutInventingAMatchDecision() {
        var association = new SourceAssociation(
                new SourceAssociationId(UUID.fromString("40000000-0000-0000-0000-000000000002")),
                SOURCE,
                PARTY_ID,
                Optional.of(ADDRESS_ID),
                Optional.of(SITE_ID),
                AssociationEvidence.newEntity(MATCH_RULESET),
                Optional.of(AssociationEvidence.newEntity(MATCH_RULESET)),
                LINKED_AT,
                Optional.empty());

        assertEquals(AssociationKind.NEW_ENTITY, association.partyEvidence().kind());
        assertTrue(association.partyEvidence().matchDecision().isEmpty());
        assertEquals(
                AssociationKind.NEW_ENTITY,
                association.siteEvidence().orElseThrow().kind());
    }

    private static SourceAssociation siteAssociation(MatchDecision partyDecision) {
        return new SourceAssociation(
                new SourceAssociationId(UUID.fromString("40000000-0000-0000-0000-000000000001")),
                SOURCE,
                PARTY_ID,
                Optional.of(ADDRESS_ID),
                Optional.of(SITE_ID),
                partyDecision,
                Optional.of(decision(MatchEntityType.SITE, SITE_ID.value(), MatchOutcome.AUTO_LINK, false)),
                LINKED_AT,
                Optional.empty());
    }

    private static MatchDecision partyDecision(MatchOutcome outcome, boolean hardConflict) {
        return decision(MatchEntityType.PARTY, PARTY_ID.value(), outcome, hardConflict);
    }

    private static MatchDecision decision(
            MatchEntityType entityType, UUID candidateId, MatchOutcome outcome, boolean hardConflict) {
        return new MatchDecision(
                entityType,
                candidateId,
                outcome,
                hardConflict ? 0 : 10_000,
                MATCH_RULESET,
                hardConflict,
                List.of(new MatchFeature(
                        hardConflict
                                ? MatchFeatureCode.AUTHORITATIVE_IDENTIFIER_CONFLICT
                                : MatchFeatureCode.INN_EXACT,
                        hardConflict ? MatchSignal.CONFLICT : MatchSignal.MATCH,
                        hardConflict ? 0 : 10_000)));
    }
}
