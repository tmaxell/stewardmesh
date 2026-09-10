package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.port.out.LoadMatchProfiles;
import io.stewardmesh.masterdata.domain.identity.PartyMatchProfile;
import io.stewardmesh.masterdata.domain.identity.SiteMatchProfile;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

public final class JdbcMatchProfileLoader implements LoadMatchProfiles {

    private final NamedParameterJdbcTemplate jdbcTemplate;

    public JdbcMatchProfileLoader(NamedParameterJdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public List<PartyMatchProfile> loadParties(Set<SupplierPartyId> partyIds) {
        Set<SupplierPartyId> requested = Set.copyOf(partyIds);
        if (requested.isEmpty()) {
            return List.of();
        }
        var parameters = new MapSqlParameterSource(
                "party_ids", requested.stream().map(SupplierPartyId::value).toList());
        return jdbcTemplate.query(
                """
                SELECT party_id, canonical_inn, canonical_ogrn, canonical_legal_name
                FROM supplier_party_match_index
                WHERE party_id IN (:party_ids)
                ORDER BY party_id
                """,
                parameters,
                (resultSet, rowNumber) -> new PartyMatchProfile(
                        new SupplierPartyId(resultSet.getObject("party_id", java.util.UUID.class)),
                        resultSet.getString("canonical_inn"),
                        resultSet.getString("canonical_ogrn"),
                        resultSet.getString("canonical_legal_name")));
    }

    @Override
    public List<SiteMatchProfile> loadSites(Set<SupplierSiteId> siteIds) {
        Set<SupplierSiteId> requested = Set.copyOf(siteIds);
        if (requested.isEmpty()) {
            return List.of();
        }
        var parameters = new MapSqlParameterSource(
                "site_ids", requested.stream().map(SupplierSiteId::value).toList());
        return jdbcTemplate.query(
                """
                SELECT site_id, party_id, canonical_inn, canonical_kpp, canonical_site_code,
                       canonical_country_code, canonical_city, canonical_address_line
                FROM supplier_site_match_index
                WHERE site_id IN (:site_ids)
                ORDER BY site_id
                """,
                parameters,
                (resultSet, rowNumber) -> new SiteMatchProfile(
                        new SupplierSiteId(resultSet.getObject("site_id", java.util.UUID.class)),
                        new SupplierPartyId(resultSet.getObject("party_id", java.util.UUID.class)),
                        resultSet.getString("canonical_inn"),
                        resultSet.getString("canonical_kpp"),
                        resultSet.getString("canonical_site_code"),
                        resultSet.getString("canonical_country_code"),
                        resultSet.getString("canonical_city"),
                        resultSet.getString("canonical_address_line")));
    }
}
