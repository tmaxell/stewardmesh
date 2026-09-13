package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.BoundPlanRequest;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.DecisionRequest;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.EvidenceInput;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.ExecutionRequest;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.ProposalRequest;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.ProposedStep;
import io.stewardmesh.masterdata.mcp.GovernedActionPlanMcpTools.SourceInput;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@Testcontainers
@SpringBootTest(properties = "stewardmesh.messaging.enabled=false")
class Phase3GovernedExecutionEndToEndIT {

    private static final String SOURCE_SYSTEM = "synthetic-erp";
    private static final String SOURCE_RECORD_ID = "supplier-phase3";
    private static final String MATCH_RULESET = "supplier-match-v1";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine");

    @Autowired
    private GovernedActionPlanMcpTools tools;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void executesOneSealedPlanExactlyOnceAcrossTheGovernedMcpBoundary() {
        UUID importId = seedValidatedSyntheticSource();
        UUID partyId = stablePartyId();

        authenticate("synthetic-agent", "mdm.steward.propose");
        var proposed = tools.createOnboardingProposal(new ProposalRequest(
                importId.toString(),
                List.of(new ProposedStep(
                        1,
                        "CREATE_SUPPLIER_PARTY",
                        partyId.toString(),
                        new SourceInput(SOURCE_SYSTEM, SOURCE_RECORD_ID, 1),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        "NO_MATCH_NEW_PARTY",
                        List.of(
                                new EvidenceInput(
                                        "SOURCE_RECORD", SOURCE_SYSTEM + ":" + SOURCE_RECORD_ID, 1),
                                new EvidenceInput("MATCH_EVALUATION", MATCH_RULESET, 1))))));

        authenticate("synthetic-reader", "mdm.supplier.read");
        var simulated = tools.simulateOnboardingPlan(
                new BoundPlanRequest(proposed.planId(), proposed.version(), proposed.hash()));
        assertEquals("EXECUTABLE", simulated.outcome());

        authenticate("synthetic-human", "mdm.steward.approve");
        var approved = tools.approveActionPlan(new DecisionRequest(
                proposed.planId(),
                proposed.version(),
                proposed.hash(),
                "phase3-approval-1",
                "Synthetic evidence reviewed"));
        assertEquals("APPROVED", approved.status());

        authenticate("synthetic-executor", "mdm.plan.execute");
        var request = new ExecutionRequest(
                proposed.planId(),
                proposed.version(),
                proposed.hash(),
                "phase3-execution-1",
                "Execute approved synthetic onboarding");
        var first = tools.executeApprovedPlan(request);
        var replay = tools.executeApprovedPlan(request);

        assertEquals(first, replay);
        assertEquals(1, first.effects().size());
        assertEquals("SupplierCreated", first.effects().getFirst().eventType());
        assertEquals("EXECUTED", text("SELECT status FROM action_plan WHERE plan_id = ?", uuid(proposed.planId())));
        assertEquals(1, count("SELECT COUNT(*) FROM action_plan_execution WHERE plan_id = ?", uuid(proposed.planId())));
        assertEquals(1, count("SELECT COUNT(*) FROM action_plan_execution_effect WHERE execution_id = ?", uuid(first.executionId())));
        assertEquals(1, count("SELECT COUNT(*) FROM audit_event WHERE plan_id = ?", uuid(proposed.planId())));
        assertEquals(1, count("SELECT COUNT(*) FROM outbox_event WHERE correlation_id = ?", uuid(first.correlationId())));
        assertEquals(1, count("SELECT COUNT(*) FROM golden_record_version WHERE entity_type = 'PARTY' AND entity_id = ?", partyId));
        assertEquals(1, count("SELECT COUNT(*) FROM source_association WHERE party_id = ?", partyId));
    }

    private UUID seedValidatedSyntheticSource() {
        UUID artifactId = UUID.randomUUID();
        UUID importId = UUID.randomUUID();
        Instant now = Instant.parse("2026-09-13T08:00:00Z");
        jdbcTemplate.update(
                "INSERT INTO intake_artifact (id, sha256, storage_key, content_type, size_bytes, created_at) VALUES (?, ?, ?, ?, ?, ?)",
                artifactId,
                "d".repeat(64),
                "synthetic/phase3.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                128,
                Timestamp.from(now));
        jdbcTemplate.update(
                "INSERT INTO import_job (id, artifact_id, source_system, status, total_rows, accepted_rows, created_at) VALUES (?, ?, ?, 'VALIDATED', 1, 1, ?)",
                importId,
                artifactId,
                SOURCE_SYSTEM,
                Timestamp.from(now));
        jdbcTemplate.update(
                """
                INSERT INTO source_record
                    (origin_system, source_record_id, source_version, import_job_id, ingested_at,
                     original_values, canonical_values, canonical_inn, normalization_ruleset)
                VALUES (?, ?, 1, ?, ?, CAST(? AS jsonb), CAST(? AS jsonb), ?, 'supplier-source-v1')
                """,
                SOURCE_SYSTEM,
                SOURCE_RECORD_ID,
                importId,
                Timestamp.from(now),
                "{\"legal_name\":\"Synthetic Phase 3 Supplier\",\"inn\":\"9902000005\"}",
                "{\"legal_name\":\"Synthetic Phase 3 Supplier\",\"inn\":\"9902000005\"}",
                "9902000005");
        jdbcTemplate.update(
                "INSERT INTO match_evaluation (origin_system, source_record_id, source_version, ruleset_id, evaluated_at) VALUES (?, ?, 1, ?, ?)",
                SOURCE_SYSTEM,
                SOURCE_RECORD_ID,
                MATCH_RULESET,
                Timestamp.from(now));
        return importId;
    }

    private static UUID stablePartyId() {
        String identity = SOURCE_SYSTEM + '\u001f' + SOURCE_RECORD_ID + '\u001f' + 1 + '\u001f' + "party";
        return UUID.nameUUIDFromBytes(identity.getBytes(StandardCharsets.UTF_8));
    }

    private static void authenticate(String subject, String scope) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                subject,
                "n/a",
                List.of(new SimpleGrantedAuthority("SCOPE_" + scope))));
    }

    private int count(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, Integer.class, argument);
    }

    private String text(String sql, Object argument) {
        return jdbcTemplate.queryForObject(sql, String.class, argument);
    }

    private static UUID uuid(String value) {
        return UUID.fromString(value);
    }
}
