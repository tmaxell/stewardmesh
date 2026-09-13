package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Objects;

/** Deterministic final gate evaluated immediately before applying an approved plan. */
public final class ActionPlanExecutionPolicy {

    public void authorize(
            GovernedActionPlan governed,
            ActionPlanApproval approval,
            ActionPlanVersion expectedVersion,
            ActionPlanHash expectedHash,
            boolean authorizedToExecute,
            ActionPlanSimulation simulation) {
        Objects.requireNonNull(governed, "governed plan must not be null");
        Objects.requireNonNull(approval, "approval must not be null");
        Objects.requireNonNull(expectedVersion, "expected version must not be null");
        Objects.requireNonNull(expectedHash, "expected hash must not be null");
        Objects.requireNonNull(simulation, "simulation must not be null");

        if (governed.status() != ActionPlanStatus.APPROVED) {
            throw violation(ExecutionPolicyCode.PLAN_NOT_APPROVED,
                    "only an approved action plan may execute");
        }
        if (!governed.version().equals(expectedVersion) || !governed.hash().equals(expectedHash)) {
            throw violation(ExecutionPolicyCode.PLAN_BINDING_MISMATCH,
                    "execution must bind the exact sealed plan version and hash");
        }
        if (approval.decision() != ApprovalDecision.APPROVE
                || !approval.planId().equals(governed.id())
                || !approval.planVersion().equals(governed.version())
                || !approval.planHash().equals(governed.hash())) {
            throw violation(ExecutionPolicyCode.APPROVAL_MISSING_OR_STALE,
                    "execution requires an approval for the exact sealed plan");
        }
        if (!authorizedToExecute) {
            throw violation(ExecutionPolicyCode.EXECUTION_NOT_AUTHORIZED,
                    "the authenticated principal may not execute action plans");
        }
        if (!simulation.planId().equals(governed.id())
                || !simulation.planVersion().equals(governed.version())
                || !simulation.planHash().equals(governed.hash())
                || simulation.outcome() != SimulationOutcome.EXECUTABLE) {
            throw violation(ExecutionPolicyCode.PRECONDITION_FAILED,
                    "all exact-plan preconditions must pass immediately before execution");
        }
    }

    private static ExecutionPolicyViolationException violation(
            ExecutionPolicyCode code, String message) {
        return new ExecutionPolicyViolationException(code, message);
    }
}
