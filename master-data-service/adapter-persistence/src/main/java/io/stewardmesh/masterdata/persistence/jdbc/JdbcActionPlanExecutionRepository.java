package io.stewardmesh.masterdata.persistence.jdbc;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanExecution;
import io.stewardmesh.masterdata.application.actionplan.AppliedActionStep;
import io.stewardmesh.masterdata.application.actionplan.ExecutionConflictException;
import io.stewardmesh.masterdata.application.actionplan.ExecutionRequestKey;
import io.stewardmesh.masterdata.application.port.out.ActionPlanExecutionRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionType;
import java.sql.Timestamp;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC ledger for exactly-once action-plan execution receipts and ordered effects. */
public final class JdbcActionPlanExecutionRepository implements ActionPlanExecutionRepository {

    private final JdbcTemplate jdbcTemplate;

    public JdbcActionPlanExecutionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
    }

    @Override
    public Optional<ActionPlanExecution> findBySubjectAndRequestKey(
            String subject, ExecutionRequestKey requestKey) {
        Objects.requireNonNull(subject, "subject must not be null");
        Objects.requireNonNull(requestKey, "request key must not be null");
        return jdbcTemplate.query("""
                SELECT execution_id, plan_id, plan_version, plan_hash, request_key,
                       executed_by_subject, reason, executed_at, correlation_id
                FROM action_plan_execution
                WHERE executed_by_subject = ? AND request_key = ?
                """, (resultSet, rowNumber) -> new ActionPlanExecution(
                        resultSet.getObject("execution_id", UUID.class),
                        new ActionPlanId(resultSet.getObject("plan_id", UUID.class)),
                        new ActionPlanVersion(resultSet.getLong("plan_version")),
                        new ActionPlanHash(resultSet.getString("plan_hash")),
                        new ExecutionRequestKey(resultSet.getString("request_key")),
                        resultSet.getString("executed_by_subject"),
                        resultSet.getString("reason"),
                        resultSet.getTimestamp("executed_at").toInstant(),
                        resultSet.getObject("correlation_id", UUID.class),
                        effects(resultSet.getObject("execution_id", UUID.class))),
                subject, requestKey.value()).stream().findFirst();
    }

    @Override
    public ActionPlanExecution save(ActionPlanExecution execution) {
        Objects.requireNonNull(execution, "execution must not be null");
        try {
            jdbcTemplate.update("""
                    INSERT INTO action_plan_execution
                        (execution_id, plan_id, plan_version, plan_hash, request_key,
                         executed_by_subject, reason, executed_at, correlation_id)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, execution.executionId(), execution.planId().value(),
                    execution.planVersion().value(), execution.planHash().value(),
                    execution.requestKey().value(), execution.executedBySubject(),
                    execution.reason(), Timestamp.from(execution.executedAt()),
                    execution.correlationId());
            execution.effects().forEach(effect -> insertEffect(execution.executionId(), effect));
            return execution;
        } catch (DataIntegrityViolationException failure) {
            throw new ExecutionConflictException(
                    "action plan or execution idempotency key already has a receipt", failure);
        }
    }

    private void insertEffect(UUID executionId, AppliedActionStep effect) {
        jdbcTemplate.update("""
                INSERT INTO action_plan_execution_effect
                    (execution_id, step_sequence, action_type, subject_type,
                     subject_id, subject_version, event_type)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, executionId, effect.sequence(), effect.actionType().name(),
                effect.subjectType(), effect.subjectId(), effect.subjectVersion(),
                effect.eventType());
    }

    private List<AppliedActionStep> effects(UUID executionId) {
        return jdbcTemplate.query("""
                SELECT step_sequence, action_type, subject_type, subject_id,
                       subject_version, event_type
                FROM action_plan_execution_effect
                WHERE execution_id = ? ORDER BY step_sequence
                """, (resultSet, rowNumber) -> new AppliedActionStep(
                        resultSet.getInt("step_sequence"),
                        ActionType.valueOf(resultSet.getString("action_type")),
                        resultSet.getString("subject_type"),
                        resultSet.getObject("subject_id", UUID.class),
                        resultSet.getLong("subject_version"),
                        resultSet.getString("event_type")), executionId);
    }
}
