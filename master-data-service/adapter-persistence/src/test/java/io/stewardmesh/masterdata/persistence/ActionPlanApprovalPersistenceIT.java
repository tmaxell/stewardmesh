package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanApprovalService;
import io.stewardmesh.masterdata.application.actionplan.ApprovalConflictException;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedApprovalActor;
import io.stewardmesh.masterdata.application.actionplan.DecideActionPlanCommand;
import io.stewardmesh.masterdata.application.port.out.ActionPlanApprovalRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApprovalPolicy;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
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

@SpringBootTest(classes = ActionPlanApprovalPersistenceIT.TestApplication.class)
class ActionPlanApprovalPersistenceIT extends PostgreSqlIntegrationTestSupport {

    private static final Instant PROPOSED_AT = Instant.parse("2026-09-12T14:00:00Z");
    private static final Instant DECIDED_AT = Instant.parse("2026-09-12T14:05:00.123456Z");

    @Autowired private ActionPlanRepository plans;
    @Autowired private ActionPlanApprovalRepository approvals;
    @Autowired private ApplicationTransaction transaction;
    @Autowired private JdbcTemplate jdbcTemplate;

    private GovernedActionPlan proposed;

    @BeforeEach
    void storePlan() {
        proposed = GovernedActionPlan.proposed(plan(new ImportJobId(insertImportGraph())));
        plans.save(proposed);
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void atomicallyStoresExactApprovalAndAdvancesPlan() {
        ActionPlanApprovalService service = new ActionPlanApprovalService(
                plans, approvals, transaction, new ActionPlanApprovalPolicy(),
                Clock.fixed(DECIDED_AT, ZoneOffset.UTC));

        ActionPlanApproval approval = service.decide(command("approval-1"),
                new AuthenticatedApprovalActor("synthetic-human", true));

        assertEquals(approval, approvals.findByPlanId(proposed.id()).orElseThrow());
        assertEquals(approval, approvals.findBySubjectAndRequestKey(
                "synthetic-human", new ApprovalRequestKey("approval-1")).orElseThrow());
        assertEquals(ActionPlanStatus.APPROVED,
                plans.findById(proposed.id()).orElseThrow().status());
    }

    @Test
    void replaysSameRequestAndRejectsKeyOrPlanConflicts() {
        ActionPlanApprovalService service = new ActionPlanApprovalService(
                plans, approvals, transaction, new ActionPlanApprovalPolicy(),
                Clock.fixed(DECIDED_AT, ZoneOffset.UTC));
        ActionPlanApproval first = service.decide(command("approval-2"),
                new AuthenticatedApprovalActor("synthetic-human", true));

        assertEquals(first, service.decide(command("approval-2"),
                new AuthenticatedApprovalActor("synthetic-human", true)));
        assertThrows(ApprovalConflictException.class, () -> approvals.save(new ActionPlanApproval(
                proposed.id(), proposed.version(), proposed.hash(), ApprovalDecision.REJECT,
                new ApprovalRequestKey("another-key"), "another-human", DECIDED_AT,
                "Conflicting second decision")));
    }

    @Test
    void databaseRequiresApprovalForDecisionAndKeepsItImmutable() {
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE action_plan SET status = 'APPROVED' WHERE plan_id = ?",
                proposed.id().value()));

        approvals.save(approval("approval-3"));

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE action_plan_approval SET reason = 'changed' WHERE plan_id = ?",
                proposed.id().value()));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "DELETE FROM action_plan_approval WHERE plan_id = ?", proposed.id().value()));
        assertEquals(ActionPlanStatus.PROPOSED,
                plans.findById(proposed.id()).orElseThrow().status());
    }

    @Test
    void failedStatusAdvanceRollsBackApprovalInsert() {
        assertThrows(RuntimeException.class, () -> transaction.execute(() -> {
            approvals.save(approval("approval-4"));
            jdbcTemplate.update("UPDATE action_plan SET status = 'EXECUTED' WHERE plan_id = ?",
                    proposed.id().value());
            return null;
        }));

        assertTrue(approvals.findByPlanId(proposed.id()).isEmpty());
        assertEquals(ActionPlanStatus.PROPOSED,
                plans.findById(proposed.id()).orElseThrow().status());
    }

    private DecideActionPlanCommand command(String key) {
        return new DecideActionPlanCommand(
                proposed.id(), proposed.version(), proposed.hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey(key), "Synthetic evidence reviewed");
    }

    private ActionPlanApproval approval(String key) {
        return new ActionPlanApproval(
                proposed.id(), proposed.version(), proposed.hash(), ApprovalDecision.APPROVE,
                new ApprovalRequestKey(key), "synthetic-human", DECIDED_AT,
                "Synthetic evidence reviewed");
    }

    private static ActionPlan plan(ImportJobId importId) {
        SourceRecordIdentity source = new SourceRecordIdentity(
                new SourceSystemRef("synthetic-erp"), "supplier-approval", 1);
        return ActionPlan.propose(
                new ActionPlanId(UUID.randomUUID()), ActionPlanVersion.initial(), importId,
                PROPOSED_AT, "synthetic-agent",
                List.of(new CreateSupplierPartyStep(
                        1, new SupplierPartyId(UUID.randomUUID()), source,
                        new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(new EvidenceReference(
                                EvidenceType.SOURCE_RECORD, "synthetic-erp:supplier-approval", 1)))));
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
                128L, Timestamp.from(PROPOSED_AT));
        jdbcTemplate.update("""
                INSERT INTO import_job (id, artifact_id, source_system, status, created_at)
                VALUES (?, ?, 'SYNTHETIC_ERP', 'VALIDATED', ?)
                """, importId, artifactId, Timestamp.from(PROPOSED_AT));
        return importId;
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(IntakePersistenceConfiguration.class)
    static class TestApplication {}
}
