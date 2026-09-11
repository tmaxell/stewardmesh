package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.identity.MatchEvaluationSummary;
import io.stewardmesh.masterdata.application.port.out.LoadMatchEvaluationSummary;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Objects;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

public final class JdbcMatchEvaluationSummaryLoader implements LoadMatchEvaluationSummary {

    private final JdbcTemplate jdbcTemplate;

    public JdbcMatchEvaluationSummaryLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Optional<MatchEvaluationSummary> find(
            SourceRecordIdentity identity, MatchRulesetId rulesetId) {
        Objects.requireNonNull(identity, "identity must not be null");
        Objects.requireNonNull(rulesetId, "rulesetId must not be null");
        var summaries = jdbcTemplate.query(
                """
                SELECT e.evaluated_at,
                       COALESCE(bool_or(d.outcome = 'REVIEW' OR d.hard_conflict), FALSE)
                           AS review_required,
                       COALESCE(bool_or(d.hard_conflict), FALSE) AS hard_conflict
                FROM match_evaluation e
                LEFT JOIN match_decision d
                  ON d.origin_system = e.origin_system
                 AND d.source_record_id = e.source_record_id
                 AND d.source_version = e.source_version
                 AND d.ruleset_id = e.ruleset_id
                WHERE e.origin_system = ?
                  AND e.source_record_id = ?
                  AND e.source_version = ?
                  AND e.ruleset_id = ?
                GROUP BY e.evaluated_at
                """,
                (resultSet, rowNumber) -> new MatchEvaluationSummary(
                        resultSet.getBoolean("review_required"),
                        resultSet.getBoolean("hard_conflict"),
                        resultSet.getTimestamp("evaluated_at").toInstant()),
                identity.originSystem().value(),
                identity.sourceRecordId(),
                identity.sourceVersion(),
                rulesetId.value());
        return summaries.stream().findFirst();
    }
}
