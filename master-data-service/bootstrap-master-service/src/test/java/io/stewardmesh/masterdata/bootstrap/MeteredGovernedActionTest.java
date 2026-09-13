package io.stewardmesh.masterdata.bootstrap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.stewardmesh.masterdata.application.actionplan.ActionPlanExecution;
import io.stewardmesh.masterdata.application.actionplan.AppliedActionStep;
import io.stewardmesh.masterdata.application.actionplan.ApprovalConflictException;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedApprovalActor;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedExecutionActor;
import io.stewardmesh.masterdata.application.actionplan.DecideActionPlanCommand;
import io.stewardmesh.masterdata.application.actionplan.ExecuteActionPlanCommand;
import io.stewardmesh.masterdata.application.actionplan.ExecutionRequestKey;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionType;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalDecision;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalRequestKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MeteredGovernedActionTest {

    private static final Instant NOW = Instant.parse("2026-09-13T12:00:00Z");
    private static final ActionPlanId PLAN_ID =
            new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-0000000009b1"));
    private static final ActionPlanHash PLAN_HASH = new ActionPlanHash("c".repeat(64));
    private static final String SENSITIVE_SUBJECT = "steward-personal-identifier";

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void separatesApprovalsFromRejectionsAndFirstDecisionsFromReplays() {
        var approving = new MeteredDecideActionPlan(
                (command, actor) -> approval(ApprovalDecision.APPROVE, NOW), registry, clock);
        var rejecting = new MeteredDecideActionPlan(
                (command, actor) -> approval(ApprovalDecision.REJECT, NOW), registry, clock);
        var replaying = new MeteredDecideActionPlan(
                (command, actor) -> approval(ApprovalDecision.APPROVE, NOW.minusSeconds(30)),
                registry,
                clock);

        approving.decide(decision(ApprovalDecision.APPROVE), actor());
        rejecting.decide(decision(ApprovalDecision.REJECT), actor());
        replaying.decide(decision(ApprovalDecision.APPROVE), actor());

        assertEquals(1, decisions("approved", "false"));
        assertEquals(1, decisions("rejected", "false"));
        assertEquals(1, decisions("approved", "true"));
    }

    @Test
    void countsRefusedDecisionsByExceptionKindOnly() {
        var refusing = new MeteredDecideActionPlan(
                (command, actor) -> {
                    throw new ApprovalConflictException("synthetic conflict");
                },
                registry,
                clock);

        assertThrows(
                ApprovalConflictException.class,
                () -> refusing.decide(decision(ApprovalDecision.APPROVE), actor()));
        assertEquals(
                1,
                registry.counter(
                                "stewardmesh.actionplan.decisions.refusals",
                                Tags.of("reason", "approvalconflictexception"))
                        .count());
    }

    @Test
    void treatsAReceiptStampedBeforeTheRequestAsAnIdempotentReplay() {
        var first = new MeteredExecuteActionPlan(
                (command, actor) -> execution(NOW), registry, clock);
        var replay = new MeteredExecuteActionPlan(
                (command, actor) -> execution(NOW.minusSeconds(60)), registry, clock);

        first.execute(execute(), executor());
        replay.execute(execute(), executor());

        assertEquals(1, executions("succeeded", "false"));
        assertEquals(1, executions("succeeded", "true"));
        assertEquals(1, registry.summary("stewardmesh.actionplan.execution.effects").count());
        assertEquals(2, registry.timer("stewardmesh.actionplan.execution.duration").count());
    }

    @Test
    void countsAndTimesAFailedExecutionWithoutLabellingTheSubject() {
        var failing = new MeteredExecuteActionPlan(
                (command, actor) -> {
                    throw new IllegalStateException("synthetic execution failure");
                },
                registry,
                clock);

        assertThrows(IllegalStateException.class, () -> failing.execute(execute(), executor()));

        assertEquals(1, executions("failed", "false"));
        assertEquals(1, registry.timer("stewardmesh.actionplan.execution.duration").count());
        assertTrue(registry.getMeters().stream()
                .flatMap(meter -> meter.getId().getTags().stream())
                .noneMatch(tag -> tag.getValue().contains(SENSITIVE_SUBJECT)
                        || tag.getValue().contains(PLAN_HASH.value())));
    }

    private double decisions(String decision, String replayed) {
        return registry.counter(
                        "stewardmesh.actionplan.decisions",
                        Tags.of("decision", decision, "replayed", replayed))
                .count();
    }

    private double executions(String outcome, String replayed) {
        return registry.counter(
                        "stewardmesh.actionplan.executions",
                        Tags.of("outcome", outcome, "replayed", replayed))
                .count();
    }

    private static ActionPlanApproval approval(ApprovalDecision decision, Instant decidedAt) {
        return new ActionPlanApproval(
                PLAN_ID,
                ActionPlanVersion.initial(),
                PLAN_HASH,
                decision,
                new ApprovalRequestKey("approval-key-1"),
                SENSITIVE_SUBJECT,
                decidedAt,
                "Synthetic evidence reviewed");
    }

    private static ActionPlanExecution execution(Instant executedAt) {
        return new ActionPlanExecution(
                UUID.fromString("00000000-0000-0000-0000-0000000009b2"),
                PLAN_ID,
                ActionPlanVersion.initial(),
                PLAN_HASH,
                new ExecutionRequestKey("execution-key-1"),
                SENSITIVE_SUBJECT,
                "Execute approved synthetic onboarding",
                executedAt,
                UUID.fromString("00000000-0000-0000-0000-0000000009b3"),
                List.of(new AppliedActionStep(
                        1,
                        ActionType.CREATE_SUPPLIER_PARTY,
                        "SUPPLIER_PARTY",
                        UUID.fromString("00000000-0000-0000-0000-0000000009b4"),
                        1,
                        "SupplierCreated")));
    }

    private static DecideActionPlanCommand decision(ApprovalDecision decision) {
        return new DecideActionPlanCommand(
                PLAN_ID,
                ActionPlanVersion.initial(),
                PLAN_HASH,
                decision,
                new ApprovalRequestKey("approval-key-1"),
                "Synthetic evidence reviewed");
    }

    private static ExecuteActionPlanCommand execute() {
        return new ExecuteActionPlanCommand(
                PLAN_ID,
                ActionPlanVersion.initial(),
                PLAN_HASH,
                new ExecutionRequestKey("execution-key-1"),
                "Execute approved synthetic onboarding");
    }

    private static AuthenticatedApprovalActor actor() {
        return new AuthenticatedApprovalActor(SENSITIVE_SUBJECT, true);
    }

    private static AuthenticatedExecutionActor executor() {
        return new AuthenticatedExecutionActor(SENSITIVE_SUBJECT, true);
    }
}
