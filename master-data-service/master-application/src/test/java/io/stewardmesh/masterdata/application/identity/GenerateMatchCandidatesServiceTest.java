package io.stewardmesh.masterdata.application.identity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.port.out.BlockMatchCandidates;
import io.stewardmesh.masterdata.domain.identity.SupplierSourceNormalizer;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecord;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GenerateMatchCandidatesServiceTest {

    private static final SupplierPartyId PARTY_1 = party("018f3f70-79b2-7d6a-bf40-3d52dc2bb201");
    private static final SupplierPartyId PARTY_2 = party("018f3f70-79b2-7d6a-bf40-3d52dc2bb202");
    private static final SupplierSiteId SITE_1 = site("018f3f70-79b2-7d6a-bf40-3d52dc2bb301");
    private static final SupplierSiteId SITE_2 = site("018f3f70-79b2-7d6a-bf40-3d52dc2bb302");

    @Test
    void generatesSortedBoundedPartyAndSiteBlocks() {
        SourceRecord source = sourceRecord(true);
        var blocker = new RecordingBlocker(
                List.of(partyBlock(PARTY_2), partyBlock(PARTY_1)),
                List.of(siteBlock(SITE_2, PARTY_2), siteBlock(SITE_1, PARTY_1)));
        var service = service(source, blocker, new CandidateBlockingPolicy(10));

        MatchCandidateBlocks result = service.execute(new GenerateMatchCandidatesCommand(source.identity(), 1));

        assertEquals(List.of(PARTY_1), result.parties().candidates().stream()
                .map(PartyCandidateBlock::partyId)
                .toList());
        assertEquals(List.of(SITE_1), result.sites().candidates().stream()
                .map(SiteCandidateBlock::siteId)
                .toList());
        assertTrue(result.parties().truncated());
        assertTrue(result.sites().truncated());
        assertEquals(2, blocker.partyFetchLimit);
        assertEquals(2, blocker.siteFetchLimit);
        assertEquals("9902000005", blocker.partyKeys.inn());
        assertEquals("1027700132195", blocker.partyKeys.ogrn());
        assertEquals("990201001", blocker.siteKeys.kpp());
        assertEquals("SITE-A", blocker.siteKeys.siteCode());
        assertEquals("PROC-A", blocker.siteKeys.procurementBusinessUnitCode());
        assertEquals("PAY", blocker.siteKeys.sitePurpose());
    }

    @Test
    void skipsSiteBlockingWhenSourceHasNoSiteContext() {
        SourceRecord source = sourceRecord(false);
        var blocker = new RecordingBlocker(List.of(partyBlock(PARTY_1)), List.of());
        var service = service(source, blocker, CandidateBlockingPolicy.conservativeDefault());

        MatchCandidateBlocks result = service.execute(new GenerateMatchCandidatesCommand(source.identity(), 5));

        assertFalse(result.parties().truncated());
        assertTrue(result.sites().candidates().isEmpty());
        assertEquals(0, blocker.siteCalls);
        assertNull(blocker.siteKeys);
    }

    @Test
    void mergesDuplicateEvidenceBeforeApplyingTheLimit() {
        SourceRecord source = sourceRecord(false);
        var blocker = new RecordingBlocker(
                List.of(
                        new PartyCandidateBlock(PARTY_1, Set.of(BlockingKeyType.PARTY_INN_EXACT)),
                        new PartyCandidateBlock(PARTY_1, Set.of(BlockingKeyType.PARTY_OGRN_EXACT))),
                List.of());
        var service = service(source, blocker, CandidateBlockingPolicy.conservativeDefault());

        MatchCandidateBlocks result = service.execute(new GenerateMatchCandidatesCommand(source.identity(), 1));

        assertFalse(result.parties().truncated());
        assertEquals(
                Set.of(BlockingKeyType.PARTY_INN_EXACT, BlockingKeyType.PARTY_OGRN_EXACT),
                result.parties().candidates().getFirst().matchedKeys());
    }

    @Test
    void rejectsUnknownSourceAndRequestsAboveConfiguredPolicy() {
        var blocker = new RecordingBlocker(List.of(), List.of());
        var missing = new GenerateMatchCandidatesService(
                ignored -> Optional.empty(), blocker, CandidateBlockingPolicy.conservativeDefault());

        assertThrows(
                SourceRecordNotFoundException.class,
                () -> missing.execute(new GenerateMatchCandidatesCommand(identity(), 1)));

        SourceRecord source = sourceRecord(false);
        var restricted = service(source, blocker, new CandidateBlockingPolicy(2));
        assertThrows(
                IllegalArgumentException.class,
                () -> restricted.execute(new GenerateMatchCandidatesCommand(source.identity(), 3)));
    }

    @Test
    void rejectsBlockingAdapterResultsThatExceedTheFetchLimit() {
        SourceRecord source = sourceRecord(false);
        var blocker = new RecordingBlocker(
                List.of(partyBlock(PARTY_1), partyBlock(PARTY_2), partyBlock(party(UUID.randomUUID().toString()))),
                List.of());
        var service = service(source, blocker, CandidateBlockingPolicy.conservativeDefault());

        assertThrows(
                CandidateBlockingPortException.class,
                () -> service.execute(new GenerateMatchCandidatesCommand(source.identity(), 1)));
    }

    @Test
    void rejectsOneSiteReturnedUnderMultipleParties() {
        SourceRecord source = sourceRecord(true);
        var blocker = new RecordingBlocker(
                List.of(),
                List.of(siteBlock(SITE_1, PARTY_1), siteBlock(SITE_1, PARTY_2)));
        var service = service(source, blocker, CandidateBlockingPolicy.conservativeDefault());

        assertThrows(
                CandidateBlockingPortException.class,
                () -> service.execute(new GenerateMatchCandidatesCommand(source.identity(), 2)));
    }

    private static GenerateMatchCandidatesService service(
            SourceRecord source,
            BlockMatchCandidates blocker,
            CandidateBlockingPolicy policy) {
        return new GenerateMatchCandidatesService(
                requested -> requested.equals(source.identity()) ? Optional.of(source) : Optional.empty(),
                blocker,
                policy);
    }

    private static SourceRecord sourceRecord(boolean siteContext) {
        Map<String, String> canonical = new LinkedHashMap<>();
        canonical.put("inn", "9902000005");
        canonical.put("ogrn", "1027700132195");
        canonical.put("country_code", "RU");
        canonical.put("postal_code", "100001");
        canonical.put("region", "TEST REGION");
        canonical.put("city", "TEST CITY");
        canonical.put("address_line", "TEST ADDRESS");
        if (siteContext) {
            canonical.put("kpp", "990201001");
            canonical.put("site_code", "SITE-A");
            canonical.put("procurement_bu_code", "PROC-A");
            canonical.put("site_purpose", "PAY");
        }
        return new SourceRecord(
                identity(),
                new ImportJobId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb101")),
                Instant.parse("2026-09-10T07:00:00Z"),
                SupplierSourceNormalizer.RULESET_ID,
                canonical,
                canonical);
    }

    private static SourceRecordIdentity identity() {
        return new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC_ERP"), "source-1", 1);
    }

    private static PartyCandidateBlock partyBlock(SupplierPartyId partyId) {
        return new PartyCandidateBlock(partyId, Set.of(BlockingKeyType.PARTY_INN_EXACT));
    }

    private static SiteCandidateBlock siteBlock(SupplierSiteId siteId, SupplierPartyId partyId) {
        return new SiteCandidateBlock(siteId, partyId, Set.of(BlockingKeyType.SITE_INN_KPP_EXACT));
    }

    private static SupplierPartyId party(String value) {
        return new SupplierPartyId(UUID.fromString(value));
    }

    private static SupplierSiteId site(String value) {
        return new SupplierSiteId(UUID.fromString(value));
    }

    private static final class RecordingBlocker implements BlockMatchCandidates {

        private final List<PartyCandidateBlock> parties;
        private final List<SiteCandidateBlock> sites;
        private PartyBlockingKeys partyKeys;
        private SiteBlockingKeys siteKeys;
        private int partyFetchLimit;
        private int siteFetchLimit;
        private int siteCalls;

        private RecordingBlocker(
                List<PartyCandidateBlock> parties, List<SiteCandidateBlock> sites) {
            this.parties = parties;
            this.sites = sites;
        }

        @Override
        public List<PartyCandidateBlock> findParties(PartyBlockingKeys keys, int fetchLimit) {
            partyKeys = keys;
            partyFetchLimit = fetchLimit;
            return parties;
        }

        @Override
        public List<SiteCandidateBlock> findSites(SiteBlockingKeys keys, int fetchLimit) {
            siteKeys = keys;
            siteFetchLimit = fetchLimit;
            siteCalls++;
            return sites;
        }
    }
}
