package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanExecution;
import io.stewardmesh.masterdata.application.actionplan.AppliedActionStep;
import io.stewardmesh.masterdata.application.actionplan.ExecutionConflictException;
import io.stewardmesh.masterdata.application.actionplan.ExecutionRequestKey;
import io.stewardmesh.masterdata.application.audit.AuditEvent;
import io.stewardmesh.masterdata.application.messaging.OutboxEvent;
import io.stewardmesh.masterdata.application.port.out.ActionPlanApprovalRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanExecutionRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.AuditEventRepository;
import io.stewardmesh.masterdata.application.port.out.OutboxEventRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.ActionType;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalDecision;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalRequestKey;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.persistence.jpa.IntakePersistenceConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = ActionPlanExecutionPersistenceIT.TestApplication.class)
class ActionPlanExecutionPersistenceIT extends PostgreSqlIntegrationTestSupport {

    private static final Instant NOW = Instant.parse("2026-09-12T17:00:00.123456Z");

    @Autowired private ActionPlanRepository plans;
    @Autowired private ActionPlanApprovalRepository approvals;
    @Autowired private ActionPlanExecutionRepository executions;
    @Autowired private AuditEventRepository audits;
    @Autowired private OutboxEventRepository outbox;
    @Autowired private ApplicationTransaction transaction;
    @Autowired private JdbcTemplate jdbcTemplate;

    private GovernedActionPlan approved;

    @BeforeEach
    void storeApprovedPlan() {
        GovernedActionPlan proposed = GovernedActionPlan.proposed(
                plan(new ImportJobId(insertImportGraph())));
        plans.save(proposed);
        approvals.save(new ActionPlanApproval(
                proposed.id(), proposed.version(), proposed.hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey("approval-" + proposed.id().value()),
                "synthetic-approver", NOW.minusSeconds(60), "Synthetic review"));
        approved = plans.save(proposed.transitionTo(ActionPlanStatus.APPROVED));
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void atomicallyStoresReceiptEffectsAuditOutboxAndFinalStatus() {
        ActionPlanExecution receipt = receipt("execute-1");

        transaction.execute(() -> {
            plans.save(approved.transitionTo(ActionPlanStatus.EXECUTING));
            executions.save(receipt);
            audits.append(audit(receipt));
            outbox.append(event(receipt));
            plans.save(approved.transitionTo(ActionPlanStatus.EXECUTING)
                    .transitionTo(ActionPlanStatus.EXECUTED));
            return null;
        });

        assertEquals(receipt, executions.findBySubjectAndRequestKey(
                "synthetic-executor", new ExecutionRequestKey("execute-1")).orElseThrow());
        assertEquals(ActionPlanStatus.EXECUTED, plans.findById(approved.id()).orElseThrow().status());
        assertEquals(1, count("audit_event", "plan_id", approved.id().value()));
        assertEquals(1, count("outbox_event", "causation_id", approved.id().value()));
        assertEquals("PENDING", jdbcTemplate.queryForObject(
                "SELECT publication_status FROM outbox_event WHERE causation_id = ?",
                String.class, approved.id().value()));
    }

    @Test
    void uniqueReceiptPreventsSecondPlanEffectAndConflictingKeyReuse() {
        ActionPlanExecution receipt = receipt("execute-2");
        executions.save(receipt);

        assertThrows(ExecutionConflictException.class, () -> executions.save(receipt));
    }

    @Test
    void failedTransactionLeavesApprovedPlanWithoutLedgers() {
        ActionPlanExecution receipt = receipt("execute-3");

        assertThrows(IllegalStateException.class, () -> transaction.execute(() -> {
            plans.save(approved.transitionTo(ActionPlanStatus.EXECUTING));
            executions.save(receipt);
            audits.append(audit(receipt));
            outbox.append(event(receipt));
            throw new IllegalStateException("synthetic rollback");
        }));

        assertTrue(executions.findBySubjectAndRequestKey(
                "synthetic-executor", new ExecutionRequestKey("execute-3")).isEmpty());
        assertEquals(ActionPlanStatus.APPROVED, plans.findById(approved.id()).orElseThrow().status());
        assertEquals(0, count("audit_event", "plan_id", approved.id().value()));
        assertEquals(0, count("outbox_event", "causation_id", approved.id().value()));
    }

    @Test
    void databaseRequiresReceiptAndProtectsImmutableBusinessContent() {
        plans.save(approved.transitionTo(ActionPlanStatus.EXECUTING));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE action_plan SET status = 'EXECUTED' WHERE plan_id = ?",
                approved.id().value()));

        ActionPlanExecution receipt = receipt("execute-4");
        executions.save(receipt);
        audits.append(audit(receipt));
        outbox.append(event(receipt));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE action_plan_execution SET reason = 'changed' WHERE execution_id = ?",
                receipt.executionId()));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE audit_event SET result = 'changed' WHERE plan_id = ?",
                approved.id().value()));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE outbox_event SET event_type = 'Changed' WHERE causation_id = ?",
                approved.id().value()));
    }

    private int count(String table, String column, UUID value) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + table + " WHERE " + column + " = ?",
                Integer.class, value);
    }

    private ActionPlanExecution receipt(String key) {
        AppliedActionStep effect = new AppliedActionStep(
                1, ActionType.CREATE_SUPPLIER_PARTY, "SUPPLIER_PARTY",
                ((CreateSupplierPartyStep) approved.plan().steps().getFirst()).partyId().value(),
                1, "SupplierCreated");
        return new ActionPlanExecution(
                UUID.randomUUID(), approved.id(), approved.version(), approved.hash(),
                new ExecutionRequestKey(key), "synthetic-executor",
                "Execute synthetic plan", NOW, UUID.randomUUID(), List.of(effect));
    }

    private static AuditEvent audit(ActionPlanExecution receipt) {
        return new AuditEvent(
                UUID.randomUUID(), receipt.planId(), receipt.planVersion(), receipt.planHash(),
                receipt.executedBySubject(), "ACTION_PLAN_EXECUTE", "SUCCEEDED",
                receipt.reason(), receipt.executedAt(), receipt.correlationId(),
                receipt.effects().stream().map(AppliedActionStep::subjectId).toList());
    }

    private static OutboxEvent event(ActionPlanExecution receipt) {
        AppliedActionStep effect = receipt.effects().getFirst();
        return new OutboxEvent(
                UUID.randomUUID(), effect.eventType(), 1, effect.subjectType(), effect.subjectId(),
                effect.subjectVersion(), "STEWARDMESH", "master-data-service",
                receipt.executedAt(), receipt.correlationId(), receipt.planId().value(),
                Map.of("planId", receipt.planId().value().toString(), "stepSequence", "1"));
    }

    private static ActionPlan plan(ImportJobId importId) {
        SourceRecordIdentity source = new SourceRecordIdentity(
                new SourceSystemRef("synthetic-erp"), "supplier-execution", 1);
        return ActionPlan.propose(
                new ActionPlanId(UUID.randomUUID()), ActionPlanVersion.initial(), importId,
                NOW.minusSeconds(120), "synthetic-agent",
                List.of(new CreateSupplierPartyStep(
                        1, new SupplierPartyId(UUID.randomUUID()), source,
                        new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(new EvidenceReference(
                                EvidenceType.SOURCE_RECORD,
                                "synthetic-erp:supplier-execution", 1)))));
    }

    private UUID insertImportGraph() {
        UUID artifactId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        jdbcTemplate.update("""
                INSERT INTO intake_artifact
                    (id, sha256, storage_key, content_type, size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, artifactId, artifactId.toString().replace("-", "").repeat(2),
                "intake/sha256/" + artifactId,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                128L, Timestamp.from(NOW.minusSeconds(180)));
        jdbcTemplate.update("""
                INSERT INTO import_job (id, artifact_id, source_system, status, created_at)
                VALUES (?, ?, 'SYNTHETIC_ERP', 'VALIDATED', ?)
                """, importId, artifactId, Timestamp.from(NOW.minusSeconds(180)));
        return importId;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(IntakePersistenceConfiguration.class)
    static class TestApplication {}
}
