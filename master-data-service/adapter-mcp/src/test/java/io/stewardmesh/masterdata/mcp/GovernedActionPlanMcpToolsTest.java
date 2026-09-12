package io.stewardmesh.masterdata.mcp;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanExecution;
import io.stewardmesh.masterdata.application.actionplan.AppliedActionStep;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedApprovalActor;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedExecutionActor;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedProposalActor;
import io.stewardmesh.masterdata.application.actionplan.DecideActionPlanCommand;
import io.stewardmesh.masterdata.application.actionplan.ExecuteActionPlanCommand;
import io.stewardmesh.masterdata.application.actionplan.ExecutionRequestKey;
import io.stewardmesh.masterdata.application.actionplan.ProposeActionPlanCommand;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulation;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.ActionType;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalDecision;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalRequestKey;
import io.stewardmesh.masterdata.domain.actionplan.AssignSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.LinkSourceRecordStep;
import io.stewardmesh.masterdata.domain.actionplan.SimulatedStep;
import io.stewardmesh.masterdata.domain.actionplan.SimulationOutcome;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import java.time.Instant;
import java.time.LocalDate;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

class GovernedActionPlanMcpToolsTest {

    private static final ActionPlanId PLAN_ID = id(ActionPlanId::new, 801);
    private static final ImportJobId IMPORT_ID = id(ImportJobId::new, 802);
    private static final SupplierPartyId PARTY_ID = id(SupplierPartyId::new, 803);
    private static final SupplierSiteId SITE_ID = id(SupplierSiteId::new, 804);
    private static final SupplierAddressId ADDRESS_ID = id(SupplierAddressId::new, 805);
    private static final BusinessUnitId BUSINESS_UNIT_ID = id(BusinessUnitId::new, 806);
    private static final SiteAssignmentId ASSIGNMENT_ID = id(SiteAssignmentId::new, 807);
    private static final SourceRecordIdentity SOURCE = new SourceRecordIdentity(
            new SourceSystemRef("synthetic-erp"), "supplier-801", 2);
    private static final Instant NOW = Instant.parse("2026-09-13T09:00:00Z");

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void publishesSixSeparateVersionedToolSchemasWithoutCallerIdentityArguments() {
        var provider = MethodToolCallbackProvider.builder().toolObjects(tools()).build();
        var definitions = Arrays.stream(provider.getToolCallbacks())
                .map(callback -> callback.getToolDefinition())
                .toList();

        assertEquals(
                Set.of(
                        "get_action_plan",
                        "simulate_onboarding_plan",
                        "create_onboarding_proposal",
                        "approve_action_plan",
                        "reject_action_plan",
                        "execute_approved_plan"),
                definitions.stream().map(definition -> definition.name()).collect(
                        java.util.stream.Collectors.toSet()));
        definitions.forEach(definition -> {
            assertFalse(definition.inputSchema().contains("subject"));
            assertFalse(definition.inputSchema().contains("authorizedTo"));
            assertTrue(definition.description().contains(":"));
        });
        String proposalSchema = definitions.stream()
                .filter(definition -> definition.name().equals("create_onboarding_proposal"))
                .findFirst()
                .orElseThrow()
                .inputSchema();
        assertTrue(proposalSchema.contains("expectedPartyVersion"));
        assertTrue(proposalSchema.contains("evidence"));
    }

    @Test
    void publishedContractNamesEveryRuntimeToolAndRequiredScope() throws Exception {
        String contract;
        try (var stream = Objects.requireNonNull(
                getClass().getResourceAsStream(
                        "/contracts/mcp/governed-action-plan-tools-v1.json"))) {
            contract = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
        var provider = MethodToolCallbackProvider.builder().toolObjects(tools()).build();

        Arrays.stream(provider.getToolCallbacks()).forEach(callback ->
                assertTrue(contract.contains("\"name\": \""
                        + callback.getToolDefinition().name()
                        + "\"")));
        assertTrue(contract.contains("\"requiredScope\": \"mdm.supplier.read\""));
        assertTrue(contract.contains("\"requiredScope\": \"mdm.steward.propose\""));
        assertTrue(contract.contains("\"requiredScope\": \"mdm.steward.approve\""));
        assertTrue(contract.contains("\"requiredScope\": \"mdm.plan.execute\""));
        assertFalse(contract.contains("authorizedToExecute"));
        assertFalse(contract.contains("proposedBySubject"));
    }

    @Test
    void requiresAuthenticationAndTheSpecificScopeBeforeCallingApplicationCode() {
        var calls = new AtomicReference<String>();
        var tools = toolsWithRead(id -> {
            calls.set("read");
            return plan();
        });

        assertThrows(
                AuthenticationCredentialsNotFoundException.class,
                () -> tools.getActionPlan(PLAN_ID.value().toString()));
        authenticate("reader", GovernedActionPlanMcpTools.PROPOSE_SCOPE);
        assertThrows(
                AccessDeniedException.class,
                () -> tools.getActionPlan(PLAN_ID.value().toString()));
        assertEquals(null, calls.get());

        authenticate("reader", GovernedActionPlanMcpTools.READ_SCOPE);
        var result = tools.getActionPlan(PLAN_ID.value().toString());
        assertEquals("read", calls.get());
        assertEquals(4, result.steps().size());
        assertEquals("supplier-801", result.steps().getFirst().targets()
                .get("sourceRecord").split(":")[1]);
    }

    @Test
    void mapsAllProposalStepShapesAndUsesOnlyTheAuthenticatedSubject() {
        var command = new AtomicReference<ProposeActionPlanCommand>();
        var actor = new AtomicReference<AuthenticatedProposalActor>();
        var tools = new GovernedActionPlanMcpTools(
                id -> plan(),
                ignored -> simulation(),
                (candidate, authenticated) -> {
                    command.set(candidate);
                    actor.set(authenticated);
                    return plan();
                },
                (candidate, authenticated) -> approval(candidate, authenticated),
                (candidate, authenticated) -> execution(candidate, authenticated));
        authenticate("agent-801", GovernedActionPlanMcpTools.PROPOSE_SCOPE);

        var result = tools.createOnboardingProposal(new GovernedActionPlanMcpTools.ProposalRequest(
                IMPORT_ID.value().toString(), proposalSteps()));

        assertEquals("agent-801", actor.get().subject());
        assertEquals(
                List.of(
                        ActionType.CREATE_SUPPLIER_PARTY,
                        ActionType.LINK_SOURCE_RECORD,
                        ActionType.CREATE_SUPPLIER_SITE,
                        ActionType.ASSIGN_SUPPLIER_SITE),
                command.get().steps().stream().map(ActionPlanStep::type).toList());
        assertEquals(PLAN_ID.value().toString(), result.planId());
    }

    @Test
    void rejectsFieldsThatDoNotBelongToTheSelectedActionShape() {
        authenticate("agent-801", GovernedActionPlanMcpTools.PROPOSE_SCOPE);
        var partyWithSite = createPartyInput();
        partyWithSite = new GovernedActionPlanMcpTools.ProposedStep(
                partyWithSite.sequence(), partyWithSite.type(), partyWithSite.partyId(), partyWithSite.source(),
                partyWithSite.expectedPartyVersion(), SITE_ID.value().toString(), partyWithSite.addressId(),
                partyWithSite.procurementBusinessUnitId(), partyWithSite.assignmentId(),
                partyWithSite.expectedSiteVersion(), partyWithSite.clientBusinessUnitId(), partyWithSite.purposes(),
                partyWithSite.validFrom(), partyWithSite.validTo(), partyWithSite.reasonCode(), partyWithSite.evidence());
        var siteWithoutAddress = proposalSteps().get(2);
        siteWithoutAddress = new GovernedActionPlanMcpTools.ProposedStep(
                siteWithoutAddress.sequence(), siteWithoutAddress.type(), siteWithoutAddress.partyId(),
                siteWithoutAddress.source(), siteWithoutAddress.expectedPartyVersion(), siteWithoutAddress.siteId(),
                null, siteWithoutAddress.procurementBusinessUnitId(), siteWithoutAddress.assignmentId(),
                siteWithoutAddress.expectedSiteVersion(), siteWithoutAddress.clientBusinessUnitId(),
                siteWithoutAddress.purposes(), siteWithoutAddress.validFrom(), siteWithoutAddress.validTo(),
                siteWithoutAddress.reasonCode(), siteWithoutAddress.evidence());
        var assignmentWithParty = proposalSteps().get(3);
        assignmentWithParty = new GovernedActionPlanMcpTools.ProposedStep(
                assignmentWithParty.sequence(), assignmentWithParty.type(), PARTY_ID.value().toString(),
                assignmentWithParty.source(), assignmentWithParty.expectedPartyVersion(), assignmentWithParty.siteId(),
                assignmentWithParty.addressId(), assignmentWithParty.procurementBusinessUnitId(),
                assignmentWithParty.assignmentId(), assignmentWithParty.expectedSiteVersion(),
                assignmentWithParty.clientBusinessUnitId(), assignmentWithParty.purposes(),
                assignmentWithParty.validFrom(), assignmentWithParty.validTo(), assignmentWithParty.reasonCode(),
                assignmentWithParty.evidence());

        GovernedActionPlanMcpTools.ProposedStep rejectedParty = partyWithSite;
        GovernedActionPlanMcpTools.ProposedStep rejectedSite = siteWithoutAddress;
        GovernedActionPlanMcpTools.ProposedStep rejectedAssignment = assignmentWithParty;
        assertAll(
                () -> assertRejectedShape(rejectedParty),
                () -> assertRejectedShape(rejectedSite),
                () -> assertRejectedShape(rejectedAssignment));
    }

    private static void assertRejectedShape(GovernedActionPlanMcpTools.ProposedStep rejected) {
        assertThrows(
                IllegalArgumentException.class,
                () -> tools().createOnboardingProposal(new GovernedActionPlanMcpTools.ProposalRequest(
                        IMPORT_ID.value().toString(), List.of(rejected))));
    }

    @Test
    void keepsApprovalRejectionAndExecutionAsDistinctScopedCalls() {
        var decision = new AtomicReference<DecideActionPlanCommand>();
        var approver = new AtomicReference<AuthenticatedApprovalActor>();
        var execution = new AtomicReference<ExecuteActionPlanCommand>();
        var executor = new AtomicReference<AuthenticatedExecutionActor>();
        var tools = new GovernedActionPlanMcpTools(
                id -> plan(),
                ignored -> simulation(),
                (candidate, authenticated) -> plan(),
                (candidate, authenticated) -> {
                    decision.set(candidate);
                    approver.set(authenticated);
                    return approval(candidate, authenticated);
                },
                (candidate, authenticated) -> {
                    execution.set(candidate);
                    executor.set(authenticated);
                    return execution(candidate, authenticated);
                });
        var boundDecision = new GovernedActionPlanMcpTools.DecisionRequest(
                PLAN_ID.value().toString(), plan().version().value(), plan().hash().value(),
                "decision-key-801", "synthetic review complete");

        authenticate("human-approver", GovernedActionPlanMcpTools.APPROVE_SCOPE);
        var rejected = tools.rejectActionPlan(boundDecision);
        assertEquals(ApprovalDecision.REJECT, decision.get().decision());
        assertEquals("human-approver", approver.get().subject());
        assertEquals(ActionPlanStatus.REJECTED.name(), rejected.status());

        var executeRequest = new GovernedActionPlanMcpTools.ExecutionRequest(
                PLAN_ID.value().toString(), plan().version().value(), plan().hash().value(),
                "execution-key-801", "apply approved synthetic plan");
        assertThrows(AccessDeniedException.class, () -> tools.executeApprovedPlan(executeRequest));

        authenticate("human-executor", GovernedActionPlanMcpTools.EXECUTE_SCOPE);
        var receipt = tools.executeApprovedPlan(executeRequest);
        assertEquals("execution-key-801", execution.get().idempotencyKey().value());
        assertEquals("human-executor", executor.get().subject());
        assertTrue(executor.get().authorizedToExecute());
        assertEquals("SupplierSiteAssigned", receipt.effects().getFirst().eventType());
    }

    @Test
    void simulationReturnsOnlyBoundedCodesAndStepIdentity() {
        authenticate("reader", GovernedActionPlanMcpTools.READ_SCOPE);
        var tools = tools();

        var result = tools.simulateOnboardingPlan(new GovernedActionPlanMcpTools.BoundPlanRequest(
                PLAN_ID.value().toString(), plan().version().value(), plan().hash().value()));

        assertEquals(SimulationOutcome.EXECUTABLE.name(), result.outcome());
        assertEquals(List.of(), result.steps().getFirst().violations());
    }

    private static GovernedActionPlanMcpTools tools() {
        return toolsWithRead(id -> plan());
    }

    private static GovernedActionPlanMcpTools toolsWithRead(
            io.stewardmesh.masterdata.application.port.in.GetActionPlan read) {
        return new GovernedActionPlanMcpTools(
                read,
                ignored -> simulation(),
                (candidate, actor) -> plan(),
                GovernedActionPlanMcpToolsTest::approval,
                GovernedActionPlanMcpToolsTest::execution);
    }

    private static GovernedActionPlan plan() {
        return GovernedActionPlan.proposed(ActionPlan.propose(
                PLAN_ID, ActionPlanVersion.initial(), IMPORT_ID, NOW, "agent-proposer",
                domainSteps()));
    }

    private static List<ActionPlanStep> domainSteps() {
        var reason = new ActionReasonCode("SYNTHETIC_ONBOARDING");
        return List.of(
                new CreateSupplierPartyStep(
                        1, PARTY_ID, SOURCE, reason, List.of(sourceEvidence(), matchEvidence())),
                new LinkSourceRecordStep(
                        2, SOURCE, PARTY_ID, 1, reason, List.of(sourceEvidence(), matchEvidence())),
                new CreateSupplierSiteStep(
                        3, SITE_ID, PARTY_ID, 2, ADDRESS_ID, BUSINESS_UNIT_ID, reason,
                        List.of(sourceEvidence())),
                new AssignSupplierSiteStep(
                        4, ASSIGNMENT_ID, SITE_ID, 1, BUSINESS_UNIT_ID,
                        Set.of(SitePurpose.PURCHASING), LocalDate.of(2026, 9, 13),
                        Optional.empty(), reason, List.of(businessUnitEvidence())));
    }

    private static ActionPlanSimulation simulation() {
        var current = plan();
        return new ActionPlanSimulation(
                current.id(), current.version(), current.hash(), SimulationOutcome.EXECUTABLE,
                List.of(new SimulatedStep(1, ActionType.CREATE_SUPPLIER_PARTY, List.of())));
    }

    private static ActionPlanApproval approval(
            DecideActionPlanCommand command, AuthenticatedApprovalActor actor) {
        return new ActionPlanApproval(
                command.planId(), command.expectedVersion(), command.expectedHash(), command.decision(),
                command.idempotencyKey(), actor.subject(), NOW, command.reason());
    }

    private static ActionPlanExecution execution(
            ExecuteActionPlanCommand command, AuthenticatedExecutionActor actor) {
        return new ActionPlanExecution(
                uuid(811), command.planId(), command.expectedVersion(), command.expectedHash(),
                new ExecutionRequestKey(command.idempotencyKey().value()), actor.subject(),
                command.reason(), NOW, uuid(812), List.of(new AppliedActionStep(
                        1, ActionType.ASSIGN_SUPPLIER_SITE, "SITE_ASSIGNMENT",
                        ASSIGNMENT_ID.value(), 1, "SupplierSiteAssigned")));
    }

    private static List<GovernedActionPlanMcpTools.ProposedStep> proposalSteps() {
        return List.of(
                createPartyInput(),
                new GovernedActionPlanMcpTools.ProposedStep(
                        2, "LINK_SOURCE_RECORD", PARTY_ID.value().toString(), sourceInput(), 1L,
                        null, null, null, null, null, null, null, null, null,
                        "SYNTHETIC_ONBOARDING", List.of(sourceEvidenceInput(), matchEvidenceInput())),
                new GovernedActionPlanMcpTools.ProposedStep(
                        3, "CREATE_SUPPLIER_SITE", PARTY_ID.value().toString(), null, 2L,
                        SITE_ID.value().toString(), ADDRESS_ID.value().toString(),
                        BUSINESS_UNIT_ID.value().toString(), null, null, null, null, null, null,
                        "SYNTHETIC_ONBOARDING", List.of(sourceEvidenceInput())),
                new GovernedActionPlanMcpTools.ProposedStep(
                        4, "ASSIGN_SUPPLIER_SITE", null, null, null, SITE_ID.value().toString(),
                        null, null, ASSIGNMENT_ID.value().toString(), 1L,
                        BUSINESS_UNIT_ID.value().toString(), List.of("PURCHASING"), "2026-09-13",
                        null, "SYNTHETIC_ONBOARDING", List.of(businessUnitEvidenceInput())));
    }

    private static GovernedActionPlanMcpTools.ProposedStep createPartyInput() {
        return new GovernedActionPlanMcpTools.ProposedStep(
                1, "CREATE_SUPPLIER_PARTY", PARTY_ID.value().toString(), sourceInput(), null,
                null, null, null, null, null, null, null, null, null,
                "SYNTHETIC_ONBOARDING", List.of(sourceEvidenceInput(), matchEvidenceInput()));
    }

    private static GovernedActionPlanMcpTools.SourceInput sourceInput() {
        return new GovernedActionPlanMcpTools.SourceInput("synthetic-erp", "supplier-801", 2);
    }

    private static GovernedActionPlanMcpTools.EvidenceInput sourceEvidenceInput() {
        return new GovernedActionPlanMcpTools.EvidenceInput(
                "SOURCE_RECORD", "synthetic-erp:supplier-801", 2);
    }

    private static GovernedActionPlanMcpTools.EvidenceInput matchEvidenceInput() {
        return new GovernedActionPlanMcpTools.EvidenceInput(
                "MATCH_EVALUATION", "supplier-match-v1", 1);
    }

    private static GovernedActionPlanMcpTools.EvidenceInput businessUnitEvidenceInput() {
        return new GovernedActionPlanMcpTools.EvidenceInput(
                "BUSINESS_UNIT_REFERENCE", "synthetic-client-806", 1);
    }

    private static EvidenceReference sourceEvidence() {
        return new EvidenceReference(EvidenceType.SOURCE_RECORD, "synthetic-erp:supplier-801", 2);
    }

    private static EvidenceReference matchEvidence() {
        return new EvidenceReference(EvidenceType.MATCH_EVALUATION, "supplier-match-v1", 1);
    }

    private static EvidenceReference businessUnitEvidence() {
        return new EvidenceReference(
                EvidenceType.BUSINESS_UNIT_REFERENCE, "synthetic-client-806", 1);
    }

    private static void authenticate(String subject, String scope) {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(
                        subject, "unused", List.of(new SimpleGrantedAuthority("SCOPE_" + scope))));
    }

    private static <T> T id(java.util.function.Function<UUID, T> constructor, int suffix) {
        return constructor.apply(uuid(suffix));
    }

    private static UUID uuid(int suffix) {
        return UUID.fromString("00000000-0000-0000-0000-000000000" + suffix);
    }
}
