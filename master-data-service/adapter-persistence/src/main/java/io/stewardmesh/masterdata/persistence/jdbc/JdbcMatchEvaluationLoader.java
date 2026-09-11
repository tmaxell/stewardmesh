package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.identity.MatchEvaluation;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluation;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchFeature;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/** Reconstructs one immutable, already bounded match evaluation for application reads. */
public class JdbcMatchEvaluationLoader implements LoadMatchEvaluation {

    private static final TypeReference<List<MatchFeature>> FEATURES_TYPE = new TypeReference<>() {};

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMatchEvaluationLoader(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<MatchEvaluation> find(IdentityResolutionKey key) {
        Objects.requireNonNull(key, "key must not be null");
        var identity = key.sourceRecordIdentity();
        var evaluatedAt = jdbcTemplate.query(
                """
                SELECT evaluated_at
                FROM match_evaluation
                WHERE origin_system = ? AND source_record_id = ?
                  AND source_version = ? AND ruleset_id = ?
                """,
                (resultSet, rowNumber) -> resultSet.getTimestamp("evaluated_at").toInstant(),
                identity.originSystem().value(), identity.sourceRecordId(), identity.sourceVersion(),
                key.rulesetId().value());
        if (evaluatedAt.isEmpty()) {
            return Optional.empty();
        }
        List<MatchDecision> decisions = jdbcTemplate.query(
                """
                SELECT entity_type, candidate_id, outcome, score_basis_points,
                       hard_conflict, features::text AS features
                FROM match_decision
                WHERE origin_system = ? AND source_record_id = ?
                  AND source_version = ? AND ruleset_id = ?
                ORDER BY entity_type, candidate_id
                """,
                (resultSet, rowNumber) -> new MatchDecision(
                        MatchEntityType.valueOf(resultSet.getString("entity_type")),
                        resultSet.getObject("candidate_id", UUID.class),
                        MatchOutcome.valueOf(resultSet.getString("outcome")),
                        resultSet.getInt("score_basis_points"),
                        key.rulesetId(), resultSet.getBoolean("hard_conflict"),
                        features(resultSet.getString("features"))),
                identity.originSystem().value(), identity.sourceRecordId(), identity.sourceVersion(),
                key.rulesetId().value());
        var parties = new ArrayList<MatchDecision>();
        var sites = new ArrayList<MatchDecision>();
        decisions.forEach(decision -> (decision.entityType() == MatchEntityType.PARTY ? parties : sites)
                .add(decision));
        return Optional.of(new MatchEvaluation(
                identity, key.rulesetId(), evaluatedAt.getFirst(), parties, sites));
    }

    private List<MatchFeature> features(String json) {
        try {
            return objectMapper.readValue(json, FEATURES_TYPE);
        } catch (JacksonException exception) {
            throw new IllegalStateException("persisted match features are invalid", exception);
        }
    }
}
