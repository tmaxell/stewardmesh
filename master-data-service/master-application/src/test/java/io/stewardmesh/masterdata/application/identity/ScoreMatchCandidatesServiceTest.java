package io.stewardmesh.masterdata.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.port.out.LoadMatchProfiles;
import io.stewardmesh.masterdata.application.port.out.MatchScoringTelemetry;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.PartyMatchProfile;
import io.stewardmesh.masterdata.domain.identity.SiteMatchProfile;
import io.stewardmesh.masterdata.domain.identity.SupplierMatchScorer;
import io.stewardmesh.masterdata.domain.identity.SupplierSourceNormalizer;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ScoreMatchCandidatesServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-10T13:00:00Z");
    private static final SupplierPartyId PARTY_ID =
            new SupplierPartyId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb201"));
    private static final SupplierSiteId SITE_ID =
            new SupplierSiteId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb301"));

    @Test
    void scoresPersistsAndMeasuresBoundedCandidates() {
        SourceRecord source = sourceRecord();
        var profiles = new StubProfiles(
                List.of(new PartyMatchProfile(
                        PARTY_ID, "9902000005", "1027700132195", "SYNTHETIC ALPHA LLC")),
                List.of(new SiteMatchProfile(
                        SITE_ID,
                        PARTY_ID,
                        "9902000005",
                        "990201001",
                        "SITE-A",
                        "RU",
                        "TEST CITY",
                        "TEST ADDRESS")));
        var stored = new MatchEvaluation[1];
        var telemetry = new RecordingTelemetry();
        var service = service(source, profiles, evaluation -> stored[0] = evaluation, telemetry);

        MatchEvaluation result = service.execute(new ScoreMatchCandidatesCommand(blocks()));

        assertSame(result, stored[0]);
        assertSame(result, telemetry.recorded);
        assertTrue(telemetry.measured);
        assertEquals(NOW, result.evaluatedAt());
        assertEquals(MatchOutcome.AUTO_LINK, result.partyDecisions().getFirst().outcome());
        assertEquals(MatchOutcome.AUTO_LINK, result.siteDecisions().getFirst().outcome());
    }

    @Test
    void rejectsIncompleteCandidateProfilesBeforePersistence() {
        SourceRecord source = sourceRecord();
        var stored = new MatchEvaluation[1];
        var service = service(
                source,
                new StubProfiles(List.of(), List.of()),
                evaluation -> stored[0] = evaluation,
                new RecordingTelemetry());

        assertThrows(
                MatchProfileException.class,
                () -> service.execute(new ScoreMatchCandidatesCommand(blocks())));
        assertNull(stored[0]);
    }

    @Test
    void rejectsSiteOwnershipThatChangedAfterBlocking() {
        SourceRecord source = sourceRecord();
        SupplierPartyId otherParty = new SupplierPartyId(UUID.randomUUID());
        var profiles = new StubProfiles(
                List.of(new PartyMatchProfile(
                        PARTY_ID, "9902000005", "1027700132195", "SYNTHETIC ALPHA LLC")),
                List.of(new SiteMatchProfile(
                        SITE_ID,
                        otherParty,
                        "9902000005",
                        "990201001",
                        "SITE-A",
                        "RU",
                        "TEST CITY",
                        "TEST ADDRESS")));

        assertThrows(
                MatchProfileException.class,
                () -> service(source, profiles, ignored -> {}, new RecordingTelemetry())
                        .execute(new ScoreMatchCandidatesCommand(blocks())));
    }

    private static ScoreMatchCandidatesService service(
            SourceRecord source,
            LoadMatchProfiles profiles,
            io.stewardmesh.masterdata.application.port.out.StoreMatchEvaluation store,
            MatchScoringTelemetry telemetry) {
        return new ScoreMatchCandidatesService(
                identity -> identity.equals(source.identity()) ? Optional.of(source) : Optional.empty(),
                profiles,
                store,
                telemetry,
                new SupplierMatchScorer(),
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static MatchCandidateBlocks blocks() {
        return new MatchCandidateBlocks(
                identity(),
                new CandidateBlockPage<>(
                        10,
                        false,
                        List.of(new PartyCandidateBlock(
                                PARTY_ID, Set.of(BlockingKeyType.PARTY_INN_EXACT)))),
                new CandidateBlockPage<>(
                        10,
                        false,
                        List.of(new SiteCandidateBlock(
                                SITE_ID, PARTY_ID, Set.of(BlockingKeyType.SITE_INN_KPP_EXACT)))));
    }

    private static SourceRecord sourceRecord() {
        Map<String, String> values = Map.of(
                "inn", "9902000005",
                "ogrn", "1027700132195",
                "legal_name", "SYNTHETIC ALPHA LLC",
                "kpp", "990201001",
                "site_code", "SITE-A",
                "country_code", "RU",
                "city", "TEST CITY",
                "address_line", "TEST ADDRESS");
        return new SourceRecord(
                identity(),
                new ImportJobId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb101")),
                NOW.minusSeconds(60),
                SupplierSourceNormalizer.RULESET_ID,
                values,
                values);
    }

    private static SourceRecordIdentity identity() {
        return new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC_ERP"), "source-1", 1);
    }

    private record StubProfiles(
            List<PartyMatchProfile> parties, List<SiteMatchProfile> sites)
            implements LoadMatchProfiles {

        @Override
        public List<PartyMatchProfile> loadParties(Set<SupplierPartyId> partyIds) {
            return parties;
        }

        @Override
        public List<SiteMatchProfile> loadSites(Set<SupplierSiteId> siteIds) {
            return sites;
        }
    }

    private static final class RecordingTelemetry implements MatchScoringTelemetry {

        private boolean measured;
        private MatchEvaluation recorded;

        @Override
        public <T> T measure(java.util.function.Supplier<T> operation) {
            measured = true;
            return operation.get();
        }

        @Override
        public void record(MatchEvaluation evaluation) {
            recorded = evaluation;
        }
    }
}
