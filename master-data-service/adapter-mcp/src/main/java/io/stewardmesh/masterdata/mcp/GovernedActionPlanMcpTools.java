package io.stewardmesh.masterdata.mcp;

import io.stewardmesh.masterdata.application.actionplan.ActionPlanExecution;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedApprovalActor;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedExecutionActor;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedProposalActor;
import io.stewardmesh.masterdata.application.actionplan.DecideActionPlanCommand;
import io.stewardmesh.masterdata.application.actionplan.ExecuteActionPlanCommand;
import io.stewardmesh.masterdata.application.actionplan.ExecutionRequestKey;
import io.stewardmesh.masterdata.application.actionplan.ProposeActionPlanCommand;
import io.stewardmesh.masterdata.application.actionplan.SimulateActionPlanCommand;
import io.stewardmesh.masterdata.application.port.in.DecideActionPlan;
import io.stewardmesh.masterdata.application.port.in.ExecuteActionPlan;
import io.stewardmesh.masterdata.application.port.in.GetActionPlan;
import io.stewardmesh.masterdata.application.port.in.ProposeActionPlan;
import io.stewardmesh.masterdata.application.port.in.SimulateActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanSimulation;
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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

/** Versioned, bounded MCP facade over separate governed application boundaries. */
public final class GovernedActionPlanMcpTools {

    static final String READ_SCOPE = "mdm.supplier.read";
    static final String PROPOSE_SCOPE = "mdm.steward.propose";
    static final String APPROVE_SCOPE = "mdm.steward.approve";
    static final String EXECUTE_SCOPE = "mdm.plan.execute";

    private final GetActionPlan getActionPlan;
    private final SimulateActionPlan simulateActionPlan;
    private final ProposeActionPlan proposeActionPlan;
    private final DecideActionPlan decideActionPlan;
    private final ExecuteActionPlan executeActionPlan;
    private final McpCallerContext callers;

    public GovernedActionPlanMcpTools(
            GetActionPlan getActionPlan,
            SimulateActionPlan simulateActionPlan,
            ProposeActionPlan proposeActionPlan,
            DecideActionPlan decideActionPlan,
            ExecuteActionPlan executeActionPlan) {
        this.getActionPlan = Objects.requireNonNull(getActionPlan, "get action plan must not be null");
        this.simulateActionPlan =
                Objects.requireNonNull(simulateActionPlan, "simulate action plan must not be null");
        this.proposeActionPlan =
                Objects.requireNonNull(proposeActionPlan, "propose action plan must not be null");
        this.decideActionPlan =
                Objects.requireNonNull(decideActionPlan, "decide action plan must not be null");
        this.executeActionPlan =
                Objects.requireNonNull(executeActionPlan, "execute action plan must not be null");
        this.callers = new McpCallerContext();
    }

    @Tool(
            name = "get_action_plan",
            description = "READ: return one sealed plan with its exact version, hash and status.")
    public PlanResult getActionPlan(
            @ToolParam(description = "Action plan UUID") String planId) {
        callers.requireScope(READ_SCOPE);
        return PlanResult.from(getActionPlan.execute(planId(planId)));
    }

    @Tool(
            name = "simulate_onboarding_plan",
            description = "SIMULATE: recheck an exact sealed plan without changing master data.")
    public SimulationResult simulateOnboardingPlan(
            @ToolParam(description = "Exact plan identity, version and SHA-256 hash")
                    BoundPlanRequest request) {
        callers.requireScope(READ_SCOPE);
        return SimulationResult.from(simulateActionPlan.execute(new SimulateActionPlanCommand(
                planId(request.planId()), version(request.expectedVersion()),
                hash(request.expectedHash()))));
    }

    @Tool(
            name = "create_onboarding_proposal",
            description = "PROPOSE: seal a review object only; this never changes master data.")
    public PlanResult createOnboardingProposal(
            @ToolParam(description = "Import UUID and 1-50 ordered typed plan steps")
                    ProposalRequest request) {
        var caller = callers.requireScope(PROPOSE_SCOPE);
        var command = new ProposeActionPlanCommand(
                new ImportJobId(uuid(request.importId(), "importId")),
                request.steps().stream().map(GovernedActionPlanMcpTools::step).toList());
        return PlanResult.from(
                proposeActionPlan.propose(command, new AuthenticatedProposalActor(caller.subject())));
    }

    @Tool(
            name = "approve_action_plan",
            description = "APPROVE: record a human approval for the exact immutable plan only.")
    public ApprovalResult approveActionPlan(
            @ToolParam(description = "Exact plan binding, idempotency key and approval reason")
                    DecisionRequest request) {
        return decide(request, ApprovalDecision.APPROVE);
    }

    @Tool(
            name = "reject_action_plan",
            description = "APPROVE class: record a human rejection for the exact immutable plan only.")
    public ApprovalResult rejectActionPlan(
            @ToolParam(description = "Exact plan binding, idempotency key and rejection reason")
                    DecisionRequest request) {
        return decide(request, ApprovalDecision.REJECT);
    }

    @Tool(
            name = "execute_approved_plan",
            description = "EXECUTE: revalidate and atomically apply one exact approved plan.")
    public ExecutionResult executeApprovedPlan(
            @ToolParam(description = "Exact approved plan binding, idempotency key and reason")
                    ExecutionRequest request) {
        var caller = callers.requireScope(EXECUTE_SCOPE);
        ActionPlanExecution execution = executeActionPlan.execute(
                new ExecuteActionPlanCommand(
                        planId(request.planId()), version(request.expectedVersion()),
                        hash(request.expectedHash()),
                        new ExecutionRequestKey(request.idempotencyKey()), request.reason()),
                new AuthenticatedExecutionActor(caller.subject(), true));
        return ExecutionResult.from(execution);
    }

    private ApprovalResult decide(DecisionRequest request, ApprovalDecision decision) {
        var caller = callers.requireScope(APPROVE_SCOPE);
        ActionPlanApproval approval = decideActionPlan.decide(
                new DecideActionPlanCommand(
                        planId(request.planId()), version(request.expectedVersion()),
                        hash(request.expectedHash()), decision,
                        new ApprovalRequestKey(request.idempotencyKey()), request.reason()),
                new AuthenticatedApprovalActor(caller.subject(), true));
        return ApprovalResult.from(approval);
    }

    private static ActionPlanStep step(ProposedStep input) {
        Objects.requireNonNull(input, "proposal step must not be null");
        ActionType type = ActionType.valueOf(required(input.type(), "type"));
        var reason = new ActionReasonCode(required(input.reasonCode(), "reasonCode"));
        List<EvidenceReference> evidence = Objects.requireNonNull(
                        input.evidence(), "evidence must not be null")
                .stream()
                .map(GovernedActionPlanMcpTools::evidence)
                .toList();
        return switch (type) {
            case CREATE_SUPPLIER_PARTY -> {
                requireShape(input, true, false, false, false);
                yield new CreateSupplierPartyStep(
                        input.sequence(), partyId(input.partyId()), source(input.source()),
                        reason, evidence);
            }
            case LINK_SOURCE_RECORD -> {
                requireShape(input, true, true, false, false);
                yield new LinkSourceRecordStep(
                        input.sequence(), source(input.source()), partyId(input.partyId()),
                        positive(input.expectedPartyVersion(), "expectedPartyVersion"),
                        reason, evidence);
            }
            case CREATE_SUPPLIER_SITE -> {
                requireShape(input, false, true, true, false);
                yield new CreateSupplierSiteStep(
                        input.sequence(), siteId(input.siteId()), partyId(input.partyId()),
                        positive(input.expectedPartyVersion(), "expectedPartyVersion"),
                        new SupplierAddressId(uuid(input.addressId(), "addressId")),
                        new BusinessUnitId(uuid(
                                input.procurementBusinessUnitId(), "procurementBusinessUnitId")),
                        reason, evidence);
            }
            case ASSIGN_SUPPLIER_SITE -> {
                requireShape(input, false, false, true, true);
                Set<SitePurpose> purposes = Objects.requireNonNull(
                                input.purposes(), "purposes must not be null")
                        .stream()
                        .map(SitePurpose::valueOf)
                        .collect(java.util.stream.Collectors.toUnmodifiableSet());
                yield new AssignSupplierSiteStep(
                        input.sequence(),
                        new SiteAssignmentId(uuid(input.assignmentId(), "assignmentId")),
                        siteId(input.siteId()),
                        positive(input.expectedSiteVersion(), "expectedSiteVersion"),
                        new BusinessUnitId(uuid(input.clientBusinessUnitId(), "clientBusinessUnitId")),
                        purposes, LocalDate.parse(required(input.validFrom(), "validFrom")),
                        Optional.ofNullable(input.validTo()).map(LocalDate::parse), reason, evidence);
            }
        };
    }

    private static void requireShape(
            ProposedStep step, boolean source, boolean partyVersion, boolean site, boolean assignment) {
        boolean valid = (source == (step.source() != null))
                && (partyVersion == (step.expectedPartyVersion() != null))
                && (site == (step.siteId() != null))
                && (assignment == (step.assignmentId() != null));
        if (!valid) {
            throw new IllegalArgumentException("step fields do not match action type " + step.type());
        }
        if (!site && step.addressId() != null || !site && step.procurementBusinessUnitId() != null) {
            throw new IllegalArgumentException("site creation fields do not match action type " + step.type());
        }
        if (!assignment && (step.expectedSiteVersion() != null
                || step.clientBusinessUnitId() != null
                || step.purposes() != null
                || step.validFrom() != null
                || step.validTo() != null)) {
            throw new IllegalArgumentException("assignment fields do not match action type " + step.type());
        }
    }

    private static EvidenceReference evidence(EvidenceInput input) {
        Objects.requireNonNull(input, "evidence item must not be null");
        return new EvidenceReference(
                EvidenceType.valueOf(required(input.type(), "evidence.type")),
                required(input.reference(), "evidence.reference"), input.version());
    }

    private static SourceRecordIdentity source(SourceInput input) {
        Objects.requireNonNull(input, "source must not be null");
        return new SourceRecordIdentity(
                new SourceSystemRef(required(input.system(), "source.system")),
                required(input.recordId(), "source.recordId"), input.version());
    }

    private static ActionPlanId planId(String value) {
        return new ActionPlanId(uuid(value, "planId"));
    }

    private static SupplierPartyId partyId(String value) {
        return new SupplierPartyId(uuid(value, "partyId"));
    }

    private static SupplierSiteId siteId(String value) {
        return new SupplierSiteId(uuid(value, "siteId"));
    }

    private static UUID uuid(String value, String field) {
        return UUID.fromString(required(value, field));
    }

    private static ActionPlanVersion version(long value) {
        return new ActionPlanVersion(value);
    }

    private static ActionPlanHash hash(String value) {
        return new ActionPlanHash(value);
    }

    private static long positive(Long value, String field) {
        if (value == null || value <= 0) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return value;
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    public record BoundPlanRequest(String planId, long expectedVersion, String expectedHash) {}

    public record DecisionRequest(
            String planId,
            long expectedVersion,
            String expectedHash,
            String idempotencyKey,
            String reason) {}

    public record ExecutionRequest(
            String planId,
            long expectedVersion,
            String expectedHash,
            String idempotencyKey,
            String reason) {}

    public record ProposalRequest(String importId, List<ProposedStep> steps) {
        public ProposalRequest {
            steps = List.copyOf(Objects.requireNonNull(steps, "steps must not be null"));
        }
    }

    /** One exact action shape; fields not belonging to the selected type must be omitted. */
    public record ProposedStep(
            int sequence,
            String type,
            String partyId,
            SourceInput source,
            Long expectedPartyVersion,
            String siteId,
            String addressId,
            String procurementBusinessUnitId,
            String assignmentId,
            Long expectedSiteVersion,
            String clientBusinessUnitId,
            List<String> purposes,
            String validFrom,
            String validTo,
            String reasonCode,
            List<EvidenceInput> evidence) {}

    public record SourceInput(String system, String recordId, long version) {}

    public record EvidenceInput(String type, String reference, long version) {}

    public record EvidenceResult(String type, String reference, long version) {
        static EvidenceResult from(EvidenceReference evidence) {
            return new EvidenceResult(
                    evidence.type().name(), evidence.reference(), evidence.version());
        }
    }

    public record StepResult(
            int sequence,
            String type,
            String risk,
            String reasonCode,
            Map<String, String> targets,
            List<EvidenceResult> evidence) {
        static StepResult from(ActionPlanStep step) {
            return new StepResult(
                    step.sequence(), step.type().name(), step.risk().name(),
                    step.reasonCode().value(), targets(step),
                    step.evidence().stream().map(EvidenceResult::from).toList());
        }

        private static Map<String, String> targets(ActionPlanStep step) {
            Map<String, String> values = new LinkedHashMap<>();
            switch (step) {
                case CreateSupplierPartyStep create -> {
                    values.put("partyId", create.partyId().value().toString());
                    values.put("sourceRecord", sourceKey(create.sourceRecord()));
                }
                case LinkSourceRecordStep link -> {
                    values.put("partyId", link.partyId().value().toString());
                    values.put("expectedPartyVersion", Long.toString(link.expectedPartyVersion()));
                    values.put("sourceRecord", sourceKey(link.sourceRecord()));
                }
                case CreateSupplierSiteStep create -> {
                    values.put("siteId", create.siteId().value().toString());
                    values.put("partyId", create.partyId().value().toString());
                    values.put("expectedPartyVersion", Long.toString(create.expectedPartyVersion()));
                    values.put("addressId", create.addressId().value().toString());
                    values.put(
                            "procurementBusinessUnitId",
                            create.procurementBusinessUnitId().value().toString());
                }
                case AssignSupplierSiteStep assign -> {
                    values.put("assignmentId", assign.assignmentId().value().toString());
                    values.put("siteId", assign.siteId().value().toString());
                    values.put("expectedSiteVersion", Long.toString(assign.expectedSiteVersion()));
                    values.put(
                            "clientBusinessUnitId",
                            assign.clientBusinessUnitId().value().toString());
                    values.put("purposes", assign.purposes().stream()
                            .map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(",")));
                    values.put("validFrom", assign.validFrom().toString());
                    assign.validTo().ifPresent(date -> values.put("validTo", date.toString()));
                }
            }
            return Map.copyOf(values);
        }

        private static String sourceKey(SourceRecordIdentity source) {
            return source.originSystem().value()
                    + ":"
                    + source.sourceRecordId()
                    + ":v"
                    + source.sourceVersion();
        }
    }

    public record PlanResult(
            String planId,
            long version,
            String hash,
            String status,
            String risk,
            String importId,
            Instant createdAt,
            List<StepResult> steps) {
        static PlanResult from(GovernedActionPlan governed) {
            var plan = governed.plan();
            return new PlanResult(
                    plan.id().value().toString(), plan.version().value(), plan.hash().value(),
                    governed.status().name(), plan.risk().name(), plan.importId().value().toString(),
                    plan.createdAt(), plan.steps().stream().map(StepResult::from).toList());
        }
    }

    public record SimulatedStepResult(int sequence, String type, List<String> violations) {}

    public record SimulationResult(
            String planId,
            long version,
            String hash,
            String outcome,
            List<SimulatedStepResult> steps) {
        static SimulationResult from(ActionPlanSimulation simulation) {
            return new SimulationResult(
                    simulation.planId().value().toString(), simulation.planVersion().value(),
                    simulation.planHash().value(), simulation.outcome().name(),
                    simulation.steps().stream()
                            .map(step -> new SimulatedStepResult(
                                    step.sequence(), step.type().name(), step.violations().stream()
                                            .map(Enum::name).toList()))
                            .toList());
        }
    }

    public record ApprovalResult(
            String planId,
            long version,
            String hash,
            String decision,
            String status,
            Instant decidedAt) {
        static ApprovalResult from(ActionPlanApproval approval) {
            return new ApprovalResult(
                    approval.planId().value().toString(), approval.planVersion().value(),
                    approval.planHash().value(), approval.decision().name(),
                    approval.resultingStatus().name(), approval.decidedAt());
        }
    }

    public record EffectResult(
            int sequence,
            String actionType,
            String subjectType,
            String subjectId,
            long subjectVersion,
            String eventType) {}

    public record ExecutionResult(
            String executionId,
            String planId,
            long version,
            String hash,
            Instant executedAt,
            String correlationId,
            List<EffectResult> effects) {
        static ExecutionResult from(ActionPlanExecution execution) {
            return new ExecutionResult(
                    execution.executionId().toString(), execution.planId().value().toString(),
                    execution.planVersion().value(), execution.planHash().value(),
                    execution.executedAt(), execution.correlationId().toString(),
                    execution.effects().stream()
                            .map(effect -> new EffectResult(
                                    effect.sequence(), effect.actionType().name(), effect.subjectType(),
                                    effect.subjectId().toString(), effect.subjectVersion(),
                                    effect.eventType()))
                            .toList());
        }
    }
}
