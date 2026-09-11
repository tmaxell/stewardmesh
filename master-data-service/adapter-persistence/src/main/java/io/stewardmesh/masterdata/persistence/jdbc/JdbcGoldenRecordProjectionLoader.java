package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttribute;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeProvenance;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierAddress;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierParty;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierSite;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRule;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Bounded JDBC reconstruction of the current golden snapshot. */
public class JdbcGoldenRecordProjectionLoader implements LoadGoldenRecordProjection {

    private static final String SELECT_CURRENT = """
            SELECT metadata.entity_type, metadata.entity_id, metadata.party_id,
                   metadata.address_id, metadata.projection_version,
                   metadata.survivorship_ruleset, metadata.projected_at
            FROM golden_record_metadata metadata
            JOIN golden_record_version version
              ON version.entity_type = metadata.entity_type
             AND version.entity_id = metadata.entity_id
             AND version.projection_version = metadata.projection_version
            WHERE metadata.entity_type = ? AND metadata.entity_id = ?
            """;
    private static final String SELECT_ATTRIBUTES = """
            SELECT attribute_name, attribute_value, origin_system, source_record_id,
                   source_version, association_id, survivorship_rule,
                   survivorship_ruleset, decided_at
            FROM golden_attribute
            WHERE entity_type = ? AND entity_id = ? AND projection_version = ?
            ORDER BY attribute_name
            """;
    private static final String SELECT_ASSOCIATIONS = """
            SELECT association_id
            FROM golden_record_source_association
            WHERE entity_type = ? AND entity_id = ? AND projection_version = ?
            ORDER BY association_id
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcGoldenRecordProjectionLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierParty> findParty(SupplierPartyId partyId) {
        Objects.requireNonNull(partyId, "partyId must not be null");
        return find(GoldenEntityType.PARTY, partyId.value()).map(snapshot -> new SupplierParty(
                partyId,
                new GoldenRecordVersion(snapshot.version()),
                new SurvivorshipRulesetId(snapshot.rulesetId()),
                snapshot.projectedAt(),
                attributes(snapshot),
                associations(snapshot)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierAddress> findAddress(SupplierAddressId addressId) {
        Objects.requireNonNull(addressId, "addressId must not be null");
        return find(GoldenEntityType.ADDRESS, addressId.value()).map(snapshot -> new SupplierAddress(
                addressId,
                new SupplierPartyId(snapshot.partyId()),
                new GoldenRecordVersion(snapshot.version()),
                new SurvivorshipRulesetId(snapshot.rulesetId()),
                snapshot.projectedAt(),
                attributes(snapshot),
                associations(snapshot)));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<SupplierSite> findSite(SupplierSiteId siteId) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        return find(GoldenEntityType.SITE, siteId.value()).map(snapshot -> new SupplierSite(
                siteId,
                new SupplierPartyId(snapshot.partyId()),
                new SupplierAddressId(Objects.requireNonNull(snapshot.addressId())),
                new GoldenRecordVersion(snapshot.version()),
                new SurvivorshipRulesetId(snapshot.rulesetId()),
                snapshot.projectedAt(),
                attributes(snapshot),
                associations(snapshot)));
    }

    private Optional<Snapshot> find(GoldenEntityType type, UUID entityId) {
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    SELECT_CURRENT,
                    (resultSet, rowNumber) -> new Snapshot(
                            GoldenEntityType.valueOf(resultSet.getString("entity_type")),
                            resultSet.getObject("entity_id", UUID.class),
                            resultSet.getObject("party_id", UUID.class),
                            resultSet.getObject("address_id", UUID.class),
                            resultSet.getLong("projection_version"),
                            resultSet.getString("survivorship_ruleset"),
                            resultSet.getTimestamp("projected_at").toInstant()),
                    type.name(),
                    entityId));
        } catch (EmptyResultDataAccessException exception) {
            return Optional.empty();
        }
    }

    private java.util.Map<GoldenAttributeName, GoldenAttribute> attributes(Snapshot snapshot) {
        var attributes = new EnumMap<GoldenAttributeName, GoldenAttribute>(GoldenAttributeName.class);
        jdbcTemplate.query(
                        SELECT_ATTRIBUTES,
                        (resultSet, rowNumber) -> {
                            var name = GoldenAttributeName.valueOf(resultSet.getString("attribute_name"));
                            var source = new SourceRecordIdentity(
                                    new SourceSystemRef(resultSet.getString("origin_system")),
                                    resultSet.getString("source_record_id"),
                                    resultSet.getLong("source_version"));
                            var provenance = new GoldenAttributeProvenance(
                                    source,
                                    new SourceAssociationId(
                                            resultSet.getObject("association_id", UUID.class)),
                                    SurvivorshipRule.valueOf(
                                            resultSet.getString("survivorship_rule")),
                                    new SurvivorshipRulesetId(
                                            resultSet.getString("survivorship_ruleset")),
                                    resultSet.getTimestamp("decided_at").toInstant());
                            return new GoldenAttribute(
                                    name, resultSet.getString("attribute_value"), provenance);
                        },
                        snapshot.type().name(),
                        snapshot.entityId(),
                        snapshot.version())
                .forEach(attribute -> attributes.put(attribute.name(), attribute));
        return attributes;
    }

    private List<SourceAssociationId> associations(Snapshot snapshot) {
        return jdbcTemplate.query(
                SELECT_ASSOCIATIONS,
                (resultSet, rowNumber) -> new SourceAssociationId(
                        resultSet.getObject("association_id", UUID.class)),
                snapshot.type().name(),
                snapshot.entityId(),
                snapshot.version());
    }

    private record Snapshot(
            GoldenEntityType type,
            UUID entityId,
            UUID partyId,
            UUID addressId,
            long version,
            String rulesetId,
            Instant projectedAt) {}
}
