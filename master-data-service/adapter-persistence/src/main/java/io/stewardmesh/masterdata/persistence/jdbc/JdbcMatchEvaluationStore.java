package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.identity.MatchEvaluation;
import io.stewardmesh.masterdata.application.identity.MatchEvaluationWriteException;
import io.stewardmesh.masterdata.application.port.out.StoreMatchEvaluation;
import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import java.sql.Timestamp;
import java.util.Objects;
import java.util.stream.Stream;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class JdbcMatchEvaluationStore implements StoreMatchEvaluation {

    private static final String INSERT_EVALUATION = """
            INSERT INTO match_evaluation
                (origin_system, source_record_id, source_version, ruleset_id, evaluated_at)
            VALUES (?, ?, ?, ?, ?)
            ON CONFLICT DO NOTHING
            """;

    private static final String INSERT_DECISION = """
            INSERT INTO match_decision
                (origin_system, source_record_id, source_version, ruleset_id,
                 entity_type, candidate_id, outcome, score_basis_points, hard_conflict, features)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))
            ON CONFLICT DO NOTHING
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMatchEvaluationStore(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    @Transactional
    public void save(MatchEvaluation evaluation) {
        Objects.requireNonNull(evaluation, "evaluation must not be null");
        var identity = evaluation.sourceRecordIdentity();
        try {
            jdbcTemplate.update(
                    INSERT_EVALUATION,
                    identity.originSystem().value(),
                    identity.sourceRecordId(),
                    identity.sourceVersion(),
                    evaluation.rulesetId().value(),
                    Timestamp.from(evaluation.evaluatedAt()));
            Stream.concat(
                            evaluation.partyDecisions().stream(),
                            evaluation.siteDecisions().stream())
                    .forEach(decision -> insertDecision(evaluation, decision));
        } catch (DataAccessException exception) {
            throw new MatchEvaluationWriteException("match evaluation could not be persisted", exception);
        }
    }

    private void insertDecision(MatchEvaluation evaluation, MatchDecision decision) {
        var identity = evaluation.sourceRecordIdentity();
        jdbcTemplate.update(
                INSERT_DECISION,
                identity.originSystem().value(),
                identity.sourceRecordId(),
                identity.sourceVersion(),
                evaluation.rulesetId().value(),
                decision.entityType().name(),
                decision.candidateId(),
                decision.outcome().name(),
                decision.scoreBasisPoints(),
                decision.hardConflict(),
                features(decision));
    }

    private String features(MatchDecision decision) {
        try {
            return objectMapper.writeValueAsString(decision.features());
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("match features cannot be serialized", exception);
        }
    }
}
