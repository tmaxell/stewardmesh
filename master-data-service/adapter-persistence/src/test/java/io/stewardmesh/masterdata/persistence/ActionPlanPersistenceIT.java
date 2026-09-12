package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanConflictException;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.ActionRisk;
import io.stewardmesh.masterdata.domain.actionplan.AssignSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.InvalidActionPlanTransitionException;
import io.stewardmesh.masterdata.domain.actionplan.LinkSourceRecordStep;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import io.stewardmesh.masterdata.persistence.jpa.IntakePersistenceConfiguration;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
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

@SpringBootTest(classes = ActionPlanPersistenceIT.TestApplication.class)
class ActionPlanPersistenceIT extends PostgreSqlIntegrationTestSupport {

    private static final Instant PROPOSED_AT =
            Instant.parse("2026-09-12T10:15:30.123456Z");

    @Autowired
    private ActionPlanRepository plans;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ImportJobId importId;

    @BeforeEach
    void createImportJob() {
        // Each test owns an import, so plans proposed in different tests never share a
        // content fingerprint and the undecided-content index stays meaningful per test.
        importId = new ImportJobId(insertImportGraph());
    }

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void roundTripsEverySealedStepShapeAndRevalidatesTheHash() {
        GovernedActionPlan governed = GovernedActionPlan.proposed(plan(completeSteps()));

        plans.save(governed);

        GovernedActionPlan loaded = plans.findById(governed.id()).orElseThrow();
        assertEquals(governed, loaded);
        assertEquals(governed.hash(), loaded.hash());
        assertEquals(governed.plan().steps(), loaded.plan().steps());
        assertEquals(PROPOSED_AT, loaded.plan().createdAt());
        assertEquals(ActionPlanStatus.PROPOSED, loaded.status());
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM action_plan_step WHERE plan_id = ?",
                Integer.class,
                governed.id().value()));
        assertEquals(ActionRisk.HIGH.name(), jdbcTemplate.queryForObject(
                "SELECT risk FROM action_plan WHERE plan_id = ?",
                String.class,
                governed.id().value()));
    }

    @Test
    void allowsOnlyOneUndecidedPlanPerProposedContent() {
        List<ActionPlanStep> steps = List.of(createParty(1));
        GovernedActionPlan first = GovernedActionPlan.proposed(plan(steps));
        GovernedActionPlan second = GovernedActionPlan.proposed(
                plan(newPlanId(), PROPOSED_AT.plusSeconds(30), "steward-2", steps));
        plans.save(first);

        assertEquals(first.fingerprint(), second.fingerprint());
        assertEquals(first, plans.findProposedByFingerprint(first.fingerprint()).orElseThrow());
        assertThrows(ActionPlanConflictException.class, () -> plans.save(second));

        plans.save(first.transitionTo(ActionPlanStatus.REJECTED));

        assertTrue(plans.findProposedByFingerprint(first.fingerprint()).isEmpty());
        assertEquals(second, plans.save(second));
    }

    @Test
    void advancesGovernedStatusAndRefusesUngovernedTransitions() {
        GovernedActionPlan proposed = GovernedActionPlan.proposed(plan(List.of(createParty(1))));
        plans.save(proposed);

        plans.save(proposed.transitionTo(ActionPlanStatus.APPROVED));

        GovernedActionPlan approved = plans.findById(proposed.id()).orElseThrow();
        assertEquals(ActionPlanStatus.APPROVED, approved.status());
        assertEquals(proposed.hash(), approved.hash());
        assertThrows(
                InvalidActionPlanTransitionException.class,
                () -> plans.save(new GovernedActionPlan(
                        proposed.plan(), ActionPlanStatus.EXECUTED)));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT lock_version FROM action_plan WHERE plan_id = ?",
                Integer.class,
                proposed.id().value()));
    }

    @Test
    void databaseRefusesUngovernedTransitionsAndSealedContentChanges() {
        GovernedActionPlan proposed = GovernedActionPlan.proposed(plan(List.of(createParty(1))));
        plans.save(proposed);
        UUID planId = proposed.id().value();

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE action_plan SET status = 'EXECUTED' WHERE plan_id = ?", planId));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE action_plan SET proposed_by_subject = 'other' WHERE plan_id = ?", planId));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "DELETE FROM action_plan WHERE plan_id = ?", planId));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE action_plan_step SET reason_code = 'CHANGED' WHERE plan_id = ?", planId));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "DELETE FROM action_plan_step_evidence WHERE plan_id = ?", planId));
    }

    @Test
    void databaseRefusesStepColumnsForeignToTheActionType() {
        GovernedActionPlan proposed = GovernedActionPlan.proposed(plan(List.of(createParty(1))));
        plans.save(proposed);

        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO action_plan_step
                    (plan_id, step_sequence, action_type, reason_code, party_id,
                     origin_system, source_record_id, source_version, expected_site_version)
                VALUES (?, 2, 'CREATE_SUPPLIER_PARTY', 'NO_MATCH_NEW_PARTY', ?, 'erp-a', 'x', 1, 3)
                """,
                proposed.id().value(),
                UUID.randomUUID()));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                """
                INSERT INTO action_plan_step
                    (plan_id, step_sequence, action_type, reason_code, party_id)
                VALUES (?, 3, 'CREATE_SUPPLIER_PARTY', 'NO_MATCH_NEW_PARTY', ?)
                """,
                proposed.id().value(),
                UUID.randomUUID()));
    }

    @Test
    void refusesCreationTimesTheDatabaseCannotReturnUnchanged() {
        GovernedActionPlan tooPrecise = GovernedActionPlan.proposed(plan(
                newPlanId(), PROPOSED_AT.plusNanos(17), "steward-1", List.of(createParty(1))));

        assertThrows(ActionPlanConflictException.class, () -> plans.save(tooPrecise));
        assertTrue(plans.findById(tooPrecise.id()).isEmpty());
    }

    @Test
    void reportsStoredContentThatNoLongerMatchesItsSealedHash() {
        GovernedActionPlan proposed = GovernedActionPlan.proposed(plan(List.of(createParty(1))));
        plans.save(proposed);
        jdbcTemplate.update(
                """
                INSERT INTO action_plan_step_evidence
                    (plan_id, step_sequence, evidence_type, evidence_reference, evidence_version)
                VALUES (?, 1, 'GOLDEN_RECORD', 'injected-evidence', 1)
                """,
                proposed.id().value());

        assertThrows(
                ActionPlanConflictException.class, () -> plans.findById(proposed.id()));
    }

    private ActionPlan plan(List<ActionPlanStep> steps) {
        return plan(newPlanId(), PROPOSED_AT, "steward-1", steps);
    }

    private ActionPlan plan(
            ActionPlanId planId, Instant createdAt, String subject, List<ActionPlanStep> steps) {
        return ActionPlan.propose(
                planId, ActionPlanVersion.initial(), importId, createdAt, subject, steps);
    }

    private UUID insertImportGraph() {
        UUID artifactId = UUID.randomUUID();
        UUID importJobId = UUID.randomUUID();
        jdbcTemplate.update(
                """
                INSERT INTO intake_artifact
                    (id, sha256, storage_key, content_type, size_bytes, created_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
                artifactId,
                artifactId.toString().replace("-", "").repeat(2),
                "intake/sha256/" + artifactId,
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                128L,
                Timestamp.from(PROPOSED_AT));
        jdbcTemplate.update(
                """
                INSERT INTO import_job (id, artifact_id, source_system, status, created_at)
                VALUES (?, ?, 'SYNTHETIC_ERP', 'VALIDATED', ?)
                """,
                importJobId,
                artifactId,
                Timestamp.from(PROPOSED_AT));
        return importJobId;
    }

    private List<ActionPlanStep> completeSteps() {
        SupplierSiteId siteId = new SupplierSiteId(UUID.randomUUID());
        return List.of(
                createParty(1),
                new LinkSourceRecordStep(
                        2,
                        sourceRecord(),
                        partyId(),
                        1,
                        new ActionReasonCode("EXACT_IDENTIFIER_MATCH"),
                        List.of(sourceEvidence())),
                new CreateSupplierSiteStep(
                        3,
                        siteId,
                        partyId(),
                        1,
                        new SupplierAddressId(UUID.randomUUID()),
                        new BusinessUnitId(UUID.randomUUID()),
                        new ActionReasonCode("NEW_OPERATING_SITE"),
                        List.of(sourceEvidence())),
                new AssignSupplierSiteStep(
                        4,
                        new SiteAssignmentId(UUID.randomUUID()),
                        siteId,
                        1,
                        new BusinessUnitId(UUID.randomUUID()),
                        Set.of(SitePurpose.PAY, SitePurpose.PURCHASING),
                        LocalDate.of(2026, 9, 12),
                        Optional.of(LocalDate.of(2027, 9, 11)),
                        new ActionReasonCode("AUTHORIZE_CLIENT_BU"),
                        List.of(new EvidenceReference(
                                EvidenceType.BUSINESS_UNIT_REFERENCE, "client-bu-1", 3))));
    }

    private static CreateSupplierPartyStep createParty(int sequence) {
        return new CreateSupplierPartyStep(
                sequence,
                partyId(),
                sourceRecord(),
                new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                List.of(
                        sourceEvidence(),
                        new EvidenceReference(
                                EvidenceType.MATCH_EVALUATION, "match-evaluation-1", 9)));
    }

    private static EvidenceReference sourceEvidence() {
        return new EvidenceReference(EvidenceType.SOURCE_RECORD, "erp-a:supplier-42", 7);
    }

    private static SourceRecordIdentity sourceRecord() {
        return new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-42", 7);
    }

    private static SupplierPartyId partyId() {
        return new SupplierPartyId(UUID.fromString("00000000-0000-0000-0000-000000000401"));
    }

    private static ActionPlanId newPlanId() {
        return new ActionPlanId(UUID.randomUUID());
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(IntakePersistenceConfiguration.class)
    static class TestApplication {}
}
