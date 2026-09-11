package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.identity.BlockingKeyType;
import io.stewardmesh.masterdata.application.identity.PartyBlockingKeys;
import io.stewardmesh.masterdata.application.identity.PartyCandidateBlock;
import io.stewardmesh.masterdata.application.identity.SiteBlockingKeys;
import io.stewardmesh.masterdata.application.identity.SiteCandidateBlock;
import io.stewardmesh.masterdata.application.port.out.BlockMatchCandidates;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** PostgreSQL-backed blocking over compact identity projections, with every query hard-bounded. */
public final class JdbcMatchCandidateBlocker implements BlockMatchCandidates {

    private static final int MAXIMUM_FETCH_LIMIT = 101;

    private static final String FIND_PARTIES = """
            SELECT party_id,
                   canonical_inn = :inn AS inn_match,
                   canonical_ogrn = CAST(:ogrn AS varchar) AS ogrn_match
            FROM supplier_party_match_index
            WHERE canonical_inn = :inn
               OR canonical_ogrn = CAST(:ogrn AS varchar)
            ORDER BY party_id
            LIMIT :fetch_limit
            """;

    private static final String FIND_SITES = """
            SELECT site_id,
                   party_id,
                   (canonical_inn = :inn
                       AND canonical_kpp = CAST(:kpp AS varchar)) AS inn_kpp_match,
                   canonical_site_code = CAST(:site_code AS varchar) AS site_code_match,
                   (canonical_country_code = :country_code
                       AND canonical_address_hash = md5(
                           :country_code || chr(31) || :city || chr(31) || :address_line)
                       AND canonical_city = :city
                       AND canonical_address_line = :address_line) AS address_match
            FROM supplier_site_match_index
            WHERE (canonical_inn = :inn
                       AND canonical_kpp = CAST(:kpp AS varchar))
               OR canonical_site_code = CAST(:site_code AS varchar)
               OR (canonical_country_code = :country_code
                       AND canonical_address_hash = md5(
                           :country_code || chr(31) || :city || chr(31) || :address_line)
                       AND canonical_city = :city
                       AND canonical_address_line = :address_line)
            ORDER BY site_id
            LIMIT :fetch_limit
            """;

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMatchCandidateBlocker(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public List<PartyCandidateBlock> findParties(PartyBlockingKeys keys, int fetchLimit) {
        Objects.requireNonNull(keys, "keys must not be null");
        requireFetchLimit(fetchLimit);
        var parameters = new MapSqlParameterSource()
                .addValue("inn", keys.inn())
                .addValue("ogrn", keys.ogrn())
                .addValue("fetch_limit", fetchLimit);
        return jdbcTemplate.query(FIND_PARTIES, parameters, (resultSet, rowNumber) -> {
            Set<BlockingKeyType> evidence = EnumSet.noneOf(BlockingKeyType.class);
            if (resultSet.getBoolean("inn_match")) {
                evidence.add(BlockingKeyType.PARTY_INN_EXACT);
            }
            if (resultSet.getBoolean("ogrn_match")) {
                evidence.add(BlockingKeyType.PARTY_OGRN_EXACT);
            }
            return new PartyCandidateBlock(
                    new SupplierPartyId(resultSet.getObject("party_id", UUID.class)), evidence);
        });
    }

    @Override
    public List<SiteCandidateBlock> findSites(SiteBlockingKeys keys, int fetchLimit) {
        Objects.requireNonNull(keys, "keys must not be null");
        requireFetchLimit(fetchLimit);
        var parameters = new MapSqlParameterSource()
                .addValue("inn", keys.inn())
                .addValue("kpp", keys.kpp())
                .addValue("site_code", keys.siteCode())
                .addValue("country_code", keys.countryCode())
                .addValue("city", keys.city())
                .addValue("address_line", keys.addressLine())
                .addValue("fetch_limit", fetchLimit);
        return jdbcTemplate.query(FIND_SITES, parameters, (resultSet, rowNumber) -> {
            Set<BlockingKeyType> evidence = EnumSet.noneOf(BlockingKeyType.class);
            if (resultSet.getBoolean("inn_kpp_match")) {
                evidence.add(BlockingKeyType.SITE_INN_KPP_EXACT);
            }
            if (resultSet.getBoolean("site_code_match")) {
                evidence.add(BlockingKeyType.SITE_CODE_EXACT);
            }
            if (resultSet.getBoolean("address_match")) {
                evidence.add(BlockingKeyType.SITE_ADDRESS_COARSE);
            }
            return new SiteCandidateBlock(
                    new SupplierSiteId(resultSet.getObject("site_id", UUID.class)),
                    new SupplierPartyId(resultSet.getObject("party_id", UUID.class)),
                    evidence);
        });
    }

    private static void requireFetchLimit(int fetchLimit) {
        if (fetchLimit <= 0 || fetchLimit > MAXIMUM_FETCH_LIMIT) {
            throw new IllegalArgumentException("fetch limit must be between 1 and 101");
        }
    }
}
