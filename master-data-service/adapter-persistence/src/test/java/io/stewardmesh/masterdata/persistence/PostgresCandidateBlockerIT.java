package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.identity.BlockingKeyType;
import io.stewardmesh.masterdata.application.identity.PartyBlockingKeys;
import io.stewardmesh.masterdata.application.identity.PartyCandidateBlock;
import io.stewardmesh.masterdata.application.identity.SiteBlockingKeys;
import io.stewardmesh.masterdata.application.identity.SiteCandidateBlock;
import io.stewardmesh.masterdata.persistence.jdbc.JdbcMatchCandidateBlocker;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class PostgresCandidateBlockerIT extends PostgreSqlIntegrationTestSupport {

    private static final UUID PARTY_1 = UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb201");
    private static final UUID PARTY_2 = UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb202");
    private static final UUID PARTY_3 = UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb203");
    private static final UUID SITE_1 = UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb301");
    private static final UUID SITE_2 = UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb302");
    private static final UUID SITE_3 = UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb303");

    private JdbcMatchCandidateBlocker blocker;

    @BeforeEach
    void resetMatchIndexes() {
        jdbcTemplate().update("DELETE FROM supplier_site_match_index");
        jdbcTemplate().update("DELETE FROM supplier_party_match_index");
        blocker = new JdbcMatchCandidateBlocker(new NamedParameterJdbcTemplate(dataSource()));
    }

    @Test
    void findsPartyCandidatesByInnOrOgrnWithCompleteEvidence() {
        insertParty(PARTY_3, "9902000005", "1027700132195");
        insertParty(PARTY_2, "7707083893", "1027700132195");
        insertParty(PARTY_1, "9902000005", "1027700000000");

        List<PartyCandidateBlock> candidates =
                blocker.findParties(new PartyBlockingKeys("9902000005", "1027700132195"), 10);

        assertEquals(List.of(PARTY_1, PARTY_2, PARTY_3), candidates.stream()
                .map(candidate -> candidate.partyId().value())
                .toList());
        assertEquals(Set.of(BlockingKeyType.PARTY_INN_EXACT), candidates.getFirst().matchedKeys());
        assertEquals(Set.of(BlockingKeyType.PARTY_OGRN_EXACT), candidates.get(1).matchedKeys());
        assertEquals(
                Set.of(BlockingKeyType.PARTY_INN_EXACT, BlockingKeyType.PARTY_OGRN_EXACT),
                candidates.getLast().matchedKeys());
    }

    @Test
    void keepsSameInnSitesSeparateAndReportsEveryMatchingBlock() {
        insertParty(PARTY_1, "9902000005", null);
        insertSite(SITE_3, PARTY_1, "9902000005", "990203003", "OTHER", "THIRD ADDRESS");
        insertSite(SITE_2, PARTY_1, "9902000005", "990202002", "SITE-A", "SECOND ADDRESS");
        insertSite(SITE_1, PARTY_1, "9902000005", "990201001", "SITE-A", "TEST ADDRESS");

        List<SiteCandidateBlock> candidates = blocker.findSites(siteKeys(), 10);

        assertEquals(List.of(SITE_1, SITE_2), candidates.stream()
                .map(candidate -> candidate.siteId().value())
                .toList());
        assertEquals(
                Set.of(
                        BlockingKeyType.SITE_INN_KPP_EXACT,
                        BlockingKeyType.SITE_CODE_EXACT,
                        BlockingKeyType.SITE_ADDRESS_COARSE),
                candidates.getFirst().matchedKeys());
        assertEquals(Set.of(BlockingKeyType.SITE_CODE_EXACT), candidates.getLast().matchedKeys());
    }

    @Test
    void appliesTheFetchLimitBeforeReturningCandidates() {
        insertParty(PARTY_3, "9902000005", null);
        insertParty(PARTY_1, "9902000005", null);
        insertParty(PARTY_2, "9902000005", null);

        List<PartyCandidateBlock> candidates =
                blocker.findParties(new PartyBlockingKeys("9902000005", null), 2);

        assertEquals(List.of(PARTY_1, PARTY_2), candidates.stream()
                .map(candidate -> candidate.partyId().value())
                .toList());
        assertThrows(
                IllegalArgumentException.class,
                () -> blocker.findParties(new PartyBlockingKeys("9902000005", null), 102));
    }

    @Test
    void findsAddressBlockWhenOptionalExactSiteKeysAreAbsent() {
        insertParty(PARTY_1, "9902000005", null);
        insertSite(SITE_1, PARTY_1, "9902000005", null, null, "TEST ADDRESS");
        var keys = new SiteBlockingKeys(
                "9902000005",
                null,
                null,
                "PROC-A",
                null,
                "RU",
                "100001",
                "TEST REGION",
                "TEST CITY",
                "TEST ADDRESS");

        List<SiteCandidateBlock> candidates = blocker.findSites(keys, 10);

        assertEquals(1, candidates.size());
        assertEquals(Set.of(BlockingKeyType.SITE_ADDRESS_COARSE), candidates.getFirst().matchedKeys());
    }

    @Test
    void usesCandidateIndexesAtSyntheticScale() {
        jdbcTemplate().update(
                """
                INSERT INTO supplier_party_match_index (party_id, canonical_inn, canonical_ogrn)
                SELECT md5('party-' || value::text)::uuid,
                       lpad(value::text, 10, '0'),
                       lpad(value::text, 13, '0')
                FROM generate_series(1, 20000) AS value
                """);
        UUID owner = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        insertParty(owner, "9999999999", null);
        jdbcTemplate().update(
                """
                INSERT INTO supplier_site_match_index
                    (site_id, party_id, canonical_inn, canonical_kpp, canonical_site_code,
                     canonical_country_code, canonical_city, canonical_address_line)
                SELECT md5('site-' || value::text)::uuid,
                       ?,
                       lpad(value::text, 10, '0'),
                       lpad(value::text, 9, '0'),
                       'SITE-' || value::text,
                       'RU',
                       'CITY-' || value::text,
                       'ADDRESS-' || value::text
                FROM generate_series(1, 20000) AS value
                """,
                owner);
        jdbcTemplate().execute("ANALYZE supplier_party_match_index");
        jdbcTemplate().execute("ANALYZE supplier_site_match_index");

        assertPlanUses("supplier_party_match_inn_idx", """
                SELECT party_id FROM supplier_party_match_index
                WHERE canonical_inn = '0000019999' ORDER BY party_id LIMIT 51
                """);
        assertPlanUses("supplier_party_match_ogrn_idx", """
                SELECT party_id FROM supplier_party_match_index
                WHERE canonical_ogrn = '0000000019999' ORDER BY party_id LIMIT 51
                """);
        assertPlanUses("supplier_site_match_inn_kpp_idx", """
                SELECT site_id FROM supplier_site_match_index
                WHERE canonical_inn = '0000019999' AND canonical_kpp = '000019999'
                ORDER BY site_id LIMIT 51
                """);
        assertPlanUses("supplier_site_match_code_idx", """
                SELECT site_id FROM supplier_site_match_index
                WHERE canonical_site_code = 'SITE-19999' ORDER BY site_id LIMIT 51
                """);
        assertPlanUses("supplier_site_match_address_idx", """
                SELECT site_id FROM supplier_site_match_index
                WHERE canonical_country_code = 'RU'
                  AND canonical_address_hash = md5('RU' || chr(31) || 'CITY-19999' || chr(31) || 'ADDRESS-19999')
                ORDER BY site_id LIMIT 51
                """);
    }

    private static void assertPlanUses(String indexName, String query) {
        String plan = String.join("\n", jdbcTemplate().queryForList("EXPLAIN " + query, String.class));
        assertTrue(plan.contains(indexName), () -> "expected " + indexName + " in plan:\n" + plan);
    }

    private static SiteBlockingKeys siteKeys() {
        return new SiteBlockingKeys(
                "9902000005",
                "990201001",
                "SITE-A",
                "PROC-A",
                "PAY",
                "RU",
                "100001",
                "TEST REGION",
                "TEST CITY",
                "TEST ADDRESS");
    }

    private static void insertParty(UUID partyId, String inn, String ogrn) {
        jdbcTemplate().update(
                """
                INSERT INTO supplier_party_match_index (party_id, canonical_inn, canonical_ogrn)
                VALUES (?, ?, ?)
                """,
                partyId,
                inn,
                ogrn);
    }

    private static void insertSite(
            UUID siteId, UUID partyId, String inn, String kpp, String siteCode, String address) {
        jdbcTemplate().update(
                """
                INSERT INTO supplier_site_match_index
                    (site_id, party_id, canonical_inn, canonical_kpp, canonical_site_code,
                     canonical_country_code, canonical_postal_code, canonical_region,
                     canonical_city, canonical_address_line)
                VALUES (?, ?, ?, ?, ?, 'RU', '100001', 'TEST REGION', 'TEST CITY', ?)
                """,
                siteId,
                partyId,
                inn,
                kpp,
                siteCode,
                address);
    }
}
