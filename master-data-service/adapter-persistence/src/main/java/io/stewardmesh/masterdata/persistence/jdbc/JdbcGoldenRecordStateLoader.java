package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordState;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordState;
import io.stewardmesh.masterdata.domain.goldenrecord.AssociationEvidence;
import io.stewardmesh.masterdata.domain.goldenrecord.AssociationKind;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenSourceAssertion;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociation;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SourcePriority;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchFeature;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Reconstructs bounded active source assertions for deterministic survivorship recalculation. */
public final class JdbcGoldenRecordStateLoader implements LoadGoldenRecordState {

    private static final TypeReference<Map<String, String>> VALUES_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<MatchFeature>> FEATURES_TYPE = new TypeReference<>() {};
    private static final SourcePriority DEFAULT_SOURCE_PRIORITY = new SourcePriority(100);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcGoldenRecordStateLoader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<GoldenRecordState> findByPartyId(SupplierPartyId partyId) {
        Objects.requireNonNull(partyId, "partyId must not be null");
        var metadata = jdbcTemplate.query(
                """
                SELECT entity_type, entity_id, address_id, projection_version
                FROM golden_record_metadata
                WHERE party_id = ?
                ORDER BY entity_type, entity_id
                """,
                (resultSet, rowNumber) -> new MetadataRow(
                        resultSet.getString("entity_type"),
                        resultSet.getObject("entity_id", UUID.class),
                        resultSet.getObject("address_id", UUID.class),
                        resultSet.getLong("projection_version")),
                partyId.value());
        Optional<MetadataRow> party = metadata.stream()
                .filter(row -> row.entityType().equals("PARTY"))
                .findFirst();
        if (party.isEmpty()) {
            return Optional.empty();
        }
        var addressVersions = new HashMap<SupplierAddressId, Long>();
        var sites = new HashMap<SupplierSiteId, GoldenRecordState.SiteState>();
        metadata.forEach(row -> {
            if (row.entityType().equals("ADDRESS")) {
                addressVersions.put(new SupplierAddressId(row.entityId()), row.version());
            } else if (row.entityType().equals("SITE")) {
                sites.put(
                        new SupplierSiteId(row.entityId()),
                        new GoldenRecordState.SiteState(
                                new SupplierAddressId(row.addressId()), row.version()));
            }
        });
        return Optional.of(new GoldenRecordState(
                partyId, party.orElseThrow().version(), assertions(partyId), addressVersions, sites));
    }

    private List<GoldenSourceAssertion> assertions(SupplierPartyId partyId) {
        return jdbcTemplate.query(
                """
                SELECT sa.association_id, sa.origin_system, sa.source_record_id,
                       sa.source_version, sa.address_id, sa.site_id,
                       sa.party_match_ruleset, sa.site_match_ruleset,
                       sa.party_association_kind, sa.site_association_kind,
                       sa.linked_at, sr.ingested_at, sr.canonical_values::text AS canonical_values
                FROM source_association sa
                JOIN source_record sr
                  ON sr.origin_system = sa.origin_system
                 AND sr.source_record_id = sa.source_record_id
                 AND sr.source_version = sa.source_version
                WHERE sa.party_id = ? AND sa.unlinked_at IS NULL
                ORDER BY sa.origin_system, sa.source_record_id, sa.source_version
                LIMIT 1001
                """,
                (resultSet, rowNumber) -> {
                    var identity = new SourceRecordIdentity(
                            new SourceSystemRef(resultSet.getString("origin_system")),
                            resultSet.getString("source_record_id"),
                            resultSet.getLong("source_version"));
                    Optional<SupplierAddressId> addressId = Optional.ofNullable(
                                    resultSet.getObject("address_id", UUID.class))
                            .map(SupplierAddressId::new);
                    Optional<SupplierSiteId> siteId = Optional.ofNullable(
                                    resultSet.getObject("site_id", UUID.class))
                            .map(SupplierSiteId::new);
                    var partyEvidence = evidence(
                            identity,
                            MatchEntityType.PARTY,
                            partyId.value(),
                            resultSet.getString("party_match_ruleset"),
                            resultSet.getString("party_association_kind"));
                    String siteRuleset = resultSet.getString("site_match_ruleset");
                    String siteKind = resultSet.getString("site_association_kind");
                    Optional<AssociationEvidence> siteEvidence = siteId.map(id -> evidence(
                            identity,
                            MatchEntityType.SITE,
                            id.value(),
                            siteRuleset,
                            siteKind));
                    var association = new SourceAssociation(
                            new SourceAssociationId(resultSet.getObject("association_id", UUID.class)),
                            identity,
                            partyId,
                            addressId,
                            siteId,
                            partyEvidence,
                            siteEvidence,
                            resultSet.getTimestamp("linked_at").toInstant(),
                            Optional.empty());
                    return new GoldenSourceAssertion(
                            identity,
                            resultSet.getTimestamp("ingested_at").toInstant(),
                            DEFAULT_SOURCE_PRIORITY,
                            association,
                            goldenValues(resultSet.getString("canonical_values")));
                },
                partyId.value());
    }

    private AssociationEvidence evidence(
            SourceRecordIdentity identity,
            MatchEntityType entityType,
            UUID targetId,
            String ruleset,
            String kind) {
        var rulesetId = new MatchRulesetId(ruleset);
        AssociationKind associationKind = AssociationKind.valueOf(kind);
        if (associationKind == AssociationKind.NEW_ENTITY) {
            return AssociationEvidence.newEntity(rulesetId);
        }
        MatchDecision decision = jdbcTemplate
                .query(
                        """
                        SELECT outcome, score_basis_points, hard_conflict, features::text AS features
                        FROM match_decision
                        WHERE origin_system = ? AND source_record_id = ? AND source_version = ?
                          AND ruleset_id = ? AND entity_type = ? AND candidate_id = ?
                        """,
                        (resultSet, rowNumber) -> new MatchDecision(
                                entityType,
                                targetId,
                                MatchOutcome.valueOf(resultSet.getString("outcome")),
                                resultSet.getInt("score_basis_points"),
                                rulesetId,
                                resultSet.getBoolean("hard_conflict"),
                                features(resultSet.getString("features"))),
                        identity.originSystem().value(),
                        identity.sourceRecordId(),
                        identity.sourceVersion(),
                        ruleset,
                        entityType.name(),
                        targetId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("association match evidence is missing"));
        return AssociationEvidence.autoLinked(decision);
    }

    private EnumMap<GoldenAttributeName, String> goldenValues(String json) {
        try {
            Map<String, String> canonical = objectMapper.readValue(json, VALUES_TYPE);
            var values = new EnumMap<GoldenAttributeName, String>(GoldenAttributeName.class);
            for (var name : GoldenAttributeName.values()) {
                String value = canonical.get(name.sourceField());
                if (value != null && !value.isBlank()) {
                    values.put(name, value);
                }
            }
            return values;
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored canonical values are invalid", exception);
        }
    }

    private List<MatchFeature> features(String json) {
        try {
            return objectMapper.readValue(json, FEATURES_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("stored match features are invalid", exception);
        }
    }

    private record MetadataRow(String entityType, UUID entityId, UUID addressId, long version) {}
}
