package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordProjection;
import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordWriteException;
import io.stewardmesh.masterdata.application.port.out.StoreGoldenRecordProjection;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttribute;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociation;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.persistence.jpa.JpaGoldenRecordMetadataStore;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

/** Transactional JDBC writer for immutable snapshots and attribute-level lineage. */
public class JdbcGoldenRecordProjectionStore implements StoreGoldenRecordProjection {

    private static final String INSERT_ASSOCIATION = """
            INSERT INTO source_association
                (association_id, origin_system, source_record_id, source_version,
                 party_id, address_id, site_id, party_match_ruleset, site_match_ruleset,
                 linked_at, unlinked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;
    private static final String INSERT_VERSION = """
            INSERT INTO golden_record_version
                (entity_type, entity_id, projection_version, party_id, address_id,
                 survivorship_ruleset, projected_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_ATTRIBUTE = """
            INSERT INTO golden_attribute
                (entity_type, entity_id, projection_version, attribute_name, attribute_value,
                 origin_system, source_record_id, source_version, association_id,
                 survivorship_rule, survivorship_ruleset, decided_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String INSERT_PROJECTION_ASSOCIATION = """
            INSERT INTO golden_record_source_association
                (entity_type, entity_id, projection_version, association_id)
            VALUES (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final JpaGoldenRecordMetadataStore metadataStore;

    public JdbcGoldenRecordProjectionStore(
            JdbcTemplate jdbcTemplate, JpaGoldenRecordMetadataStore metadataStore) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.metadataStore = Objects.requireNonNull(metadataStore, "metadataStore must not be null");
    }

    @Override
    @Transactional
    public void save(GoldenRecordProjection projection) {
        Objects.requireNonNull(projection, "projection must not be null");
        try {
            projection.sourceAssociations().forEach(this::saveAssociation);
            metadataStore.advance(projection.party());
            projection.addresses().forEach(metadataStore::advance);
            projection.sites().forEach(metadataStore::advance);

            var party = projection.party();
            insertSnapshot(new Snapshot(
                    GoldenEntityType.PARTY,
                    party.id().value(),
                    party.id().value(),
                    null,
                    party.version().value(),
                    party.rulesetId().value(),
                    party.projectedAt(),
                    party.attributes(),
                    party.sourceAssociations()));
            projection.addresses().forEach(address -> insertSnapshot(new Snapshot(
                    GoldenEntityType.ADDRESS,
                    address.id().value(),
                    address.partyId().value(),
                    address.id().value(),
                    address.version().value(),
                    address.rulesetId().value(),
                    address.projectedAt(),
                    address.attributes(),
                    address.sourceAssociations())));
            projection.sites().forEach(site -> insertSnapshot(new Snapshot(
                    GoldenEntityType.SITE,
                    site.id().value(),
                    site.partyId().value(),
                    site.addressId().value(),
                    site.version().value(),
                    site.rulesetId().value(),
                    site.projectedAt(),
                    site.attributes(),
                    site.sourceAssociations())));
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new GoldenRecordWriteException("golden record projection could not be persisted", exception);
        }
    }

    private void saveAssociation(SourceAssociation association) {
        var source = association.sourceRecord();
        jdbcTemplate.update(
                connection -> {
                    var statement = connection.prepareStatement(INSERT_ASSOCIATION);
                    statement.setObject(1, association.id().value());
                    statement.setString(2, source.originSystem().value());
                    statement.setString(3, source.sourceRecordId());
                    statement.setLong(4, source.sourceVersion());
                    statement.setObject(5, association.partyId().value());
                    setUuid(statement, 6, association.addressId().map(id -> id.value()).orElse(null));
                    setUuid(statement, 7, association.siteId().map(id -> id.value()).orElse(null));
                    statement.setString(8, association.partyDecision().rulesetId().value());
                    statement.setString(
                            9,
                            association.siteDecision()
                                    .map(decision -> decision.rulesetId().value())
                                    .orElse(null));
                    statement.setTimestamp(10, Timestamp.from(association.linkedAt()));
                    statement.setTimestamp(
                            11, association.unlinkedAt().map(Timestamp::from).orElse(null));
                    return statement;
                });

        var stored = jdbcTemplate.queryForObject(
                """
                SELECT association_id, origin_system, source_record_id, source_version,
                       party_id, address_id, site_id, party_match_ruleset, site_match_ruleset,
                       linked_at, unlinked_at
                FROM source_association WHERE association_id = ?
                """,
                (resultSet, rowNumber) -> new AssociationRow(
                        resultSet.getObject("association_id", UUID.class),
                        resultSet.getString("origin_system"),
                        resultSet.getString("source_record_id"),
                        resultSet.getLong("source_version"),
                        resultSet.getObject("party_id", UUID.class),
                        resultSet.getObject("address_id", UUID.class),
                        resultSet.getObject("site_id", UUID.class),
                        resultSet.getString("party_match_ruleset"),
                        resultSet.getString("site_match_ruleset"),
                        resultSet.getTimestamp("linked_at").toInstant(),
                        resultSet.getTimestamp("unlinked_at") == null
                                ? null
                                : resultSet.getTimestamp("unlinked_at").toInstant()),
                association.id().value());
        if (!stored.sameImmutableValues(association)) {
            throw new IllegalArgumentException("association identity already contains different evidence");
        }
        if (association.unlinkedAt().isPresent() && stored.unlinkedAt() == null) {
            jdbcTemplate.update(
                    """
                    UPDATE source_association SET unlinked_at = ?
                    WHERE association_id = ? AND unlinked_at IS NULL
                    """,
                    Timestamp.from(association.unlinkedAt().orElseThrow()),
                    association.id().value());
        } else if (association.unlinkedAt().isEmpty() && stored.unlinkedAt() != null) {
            throw new IllegalArgumentException("an ended source association cannot become active again");
        }
    }

    private void insertSnapshot(Snapshot snapshot) {
        jdbcTemplate.update(
                connection -> {
                    var statement = connection.prepareStatement(INSERT_VERSION);
                    statement.setString(1, snapshot.entityType().name());
                    statement.setObject(2, snapshot.entityId());
                    statement.setLong(3, snapshot.version());
                    statement.setObject(4, snapshot.partyId());
                    setUuid(statement, 5, snapshot.addressId());
                    statement.setString(6, snapshot.rulesetId());
                    statement.setTimestamp(7, Timestamp.from(snapshot.projectedAt()));
                    return statement;
                });
        snapshot.attributes().values().forEach(attribute -> insertAttribute(snapshot, attribute));
        snapshot.associations().forEach(associationId -> jdbcTemplate.update(
                INSERT_PROJECTION_ASSOCIATION,
                snapshot.entityType().name(),
                snapshot.entityId(),
                snapshot.version(),
                associationId.value()));
    }

    private void insertAttribute(Snapshot snapshot, GoldenAttribute attribute) {
        var provenance = attribute.provenance();
        var source = provenance.sourceRecord();
        jdbcTemplate.update(
                INSERT_ATTRIBUTE,
                snapshot.entityType().name(),
                snapshot.entityId(),
                snapshot.version(),
                attribute.name().name(),
                attribute.value(),
                source.originSystem().value(),
                source.sourceRecordId(),
                source.sourceVersion(),
                provenance.associationId().value(),
                provenance.rule().name(),
                provenance.rulesetId().value(),
                Timestamp.from(provenance.decidedAt()));
    }

    private static void setUuid(java.sql.PreparedStatement statement, int index, UUID value)
            throws java.sql.SQLException {
        if (value == null) {
            statement.setNull(index, Types.OTHER);
        } else {
            statement.setObject(index, value);
        }
    }

    private record Snapshot(
            GoldenEntityType entityType,
            UUID entityId,
            UUID partyId,
            UUID addressId,
            long version,
            String rulesetId,
            Instant projectedAt,
            Map<GoldenAttributeName, GoldenAttribute> attributes,
            List<SourceAssociationId> associations) {}

    private record AssociationRow(
            UUID id,
            String originSystem,
            String sourceRecordId,
            long sourceVersion,
            UUID partyId,
            UUID addressId,
            UUID siteId,
            String partyRuleset,
            String siteRuleset,
            Instant linkedAt,
            Instant unlinkedAt) {

        boolean sameImmutableValues(SourceAssociation association) {
            var source = association.sourceRecord();
            return id.equals(association.id().value())
                    && originSystem.equals(source.originSystem().value())
                    && sourceRecordId.equals(source.sourceRecordId())
                    && sourceVersion == source.sourceVersion()
                    && partyId.equals(association.partyId().value())
                    && Objects.equals(
                            addressId, association.addressId().map(id -> id.value()).orElse(null))
                    && Objects.equals(siteId, association.siteId().map(id -> id.value()).orElse(null))
                    && partyRuleset.equals(association.partyDecision().rulesetId().value())
                    && Objects.equals(
                            siteRuleset,
                            association.siteDecision()
                                    .map(decision -> decision.rulesetId().value())
                                    .orElse(null))
                    && linkedAt.equals(association.linkedAt());
        }
    }
}
