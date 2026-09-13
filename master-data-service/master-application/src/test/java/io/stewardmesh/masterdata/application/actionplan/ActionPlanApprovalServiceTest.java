package io.stewardmesh.masterdata.application.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.ActionPlanApprovalRepository;
import io.stewardmesh.masterdata.application.port.out.ActionPlanRepository;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApproval;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanApprovalPolicy;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanFingerprint;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanHash;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalDecision;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalPolicyCode;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalPolicyViolationException;
import io.stewardmesh.masterdata.domain.actionplan.ApprovalRequestKey;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class ActionPlanApprovalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-12T13:00:00.123456789Z");

    @Test
    void storesDecisionAndStatusAtomically() {
        InMemoryPlans plans = new InMemoryPlans(proposed());
        InMemoryApprovals approvals = new InMemoryApprovals();
        var service = service(plans, approvals);

        ActionPlanApproval result = service.decide(command(ApprovalDecision.APPROVE, "key-1"),
                new AuthenticatedApprovalActor("human-approver", true));

        assertEquals(Instant.parse("2026-09-12T13:00:00.123456Z"), result.decidedAt());
        assertEquals(ActionPlanStatus.APPROVED, plans.current.status());
        assertEquals(result, approvals.byPlan.get(result.planId()));
    }

    @Test
    void repeatsTheSameDecisionWithoutASecondWrite() {
        InMemoryPlans plans = new InMemoryPlans(proposed());
        InMemoryApprovals approvals = new InMemoryApprovals();
        var service = service(plans, approvals);

        ActionPlanApproval first = service.decide(command(ApprovalDecision.REJECT, "key-2"),
                new AuthenticatedApprovalActor("human-approver", true));
        ActionPlanApproval replay = service.decide(command(ApprovalDecision.REJECT, "key-2"),
                new AuthenticatedApprovalActor("human-approver", true));

        assertSame(first, replay);
        assertEquals(1, approvals.saveCalls);
        assertEquals(1, plans.saveCalls);
    }

    @Test
    void rejectsReusingAKeyForDifferentContent() {
        InMemoryPlans plans = new InMemoryPlans(proposed());
        InMemoryApprovals approvals = new InMemoryApprovals();
        var service = service(plans, approvals);
        service.decide(command(ApprovalDecision.APPROVE, "key-3"),
                new AuthenticatedApprovalActor("human-approver", true));

        DecideActionPlanCommand conflicting = new DecideActionPlanCommand(
                plans.current.id(), plans.current.version(), plans.current.hash(),
                ApprovalDecision.REJECT, new ApprovalRequestKey("key-3"), "Reviewed");

        assertThrows(ApprovalConflictException.class, () -> service.decide(
                conflicting, new AuthenticatedApprovalActor("human-approver", true)));
    }

    @Test
    void appliesAuthorizationInsideTheApplicationBoundary() {
        InMemoryPlans plans = new InMemoryPlans(proposed());
        var service = service(plans, new InMemoryApprovals());

        ApprovalPolicyViolationException failure = assertThrows(
                ApprovalPolicyViolationException.class,
                () -> service.decide(command(ApprovalDecision.APPROVE, "key-4"),
                        new AuthenticatedApprovalActor("human-viewer", false)));

        assertEquals(ApprovalPolicyCode.APPROVAL_NOT_AUTHORIZED, failure.code());
        assertEquals(ActionPlanStatus.PROPOSED, plans.current.status());
    }

    @Test
    void rollsBackBothWritesWhenStatusAdvanceFails() {
        InMemoryPlans plans = new InMemoryPlans(proposed());
        InMemoryApprovals approvals = new InMemoryApprovals();
        plans.failSave = true;
        var service = new ActionPlanApprovalService(
                plans, approvals, new RollingBackApprovalTransaction(plans, approvals),
                new ActionPlanApprovalPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> service.decide(
                command(ApprovalDecision.APPROVE, "key-5"),
                new AuthenticatedApprovalActor("human-approver", true)));
        assertEquals(0, approvals.byPlan.size());
        assertEquals(ActionPlanStatus.PROPOSED, plans.current.status());
    }

    private static ActionPlanApprovalService service(
            InMemoryPlans plans, InMemoryApprovals approvals) {
        return new ActionPlanApprovalService(
                plans, approvals, new DirectTransaction(),
                new ActionPlanApprovalPolicy(), Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static DecideActionPlanCommand command(ApprovalDecision decision, String key) {
        GovernedActionPlan plan = proposed();
        return new DecideActionPlanCommand(
                plan.id(), plan.version(), plan.hash(), decision,
                new ApprovalRequestKey(key), "Reviewed");
    }

    private static GovernedActionPlan proposed() {
        SourceRecordIdentity source = new SourceRecordIdentity(
                new SourceSystemRef("synthetic-erp"), "supplier-42", 7);
        ActionPlan plan = ActionPlan.propose(
                new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000711")),
                ActionPlanVersion.initial(),
                new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000712")),
                Instant.parse("2026-09-12T11:00:00Z"),
                "agent-proposer",
                List.of(new CreateSupplierPartyStep(
                        1,
                        new SupplierPartyId(
                                UUID.fromString("00000000-0000-0000-0000-000000000713")),
                        source,
                        new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(new EvidenceReference(
                                EvidenceType.SOURCE_RECORD, "synthetic-erp:supplier-42", 7)))));
        return GovernedActionPlan.proposed(plan);
    }

    private static final class InMemoryPlans implements ActionPlanRepository {
        private GovernedActionPlan current;
        private int saveCalls;
        private boolean failSave;

        private InMemoryPlans(GovernedActionPlan current) {
            this.current = current;
        }

        @Override
        public Optional<GovernedActionPlan> findById(ActionPlanId id) {
            return current.id().equals(id) ? Optional.of(current) : Optional.empty();
        }

        @Override
        public Optional<GovernedActionPlan> findProposedByFingerprint(
                ActionPlanFingerprint fingerprint) {
            return Optional.empty();
        }

        @Override
        public GovernedActionPlan save(GovernedActionPlan plan) {
            if (failSave) {
                throw new IllegalStateException("synthetic status failure");
            }
            saveCalls++;
            current = plan;
            return plan;
        }
    }

    private static final class DirectTransaction implements ApplicationTransaction {

        @Override
        public <T> T execute(Supplier<T> operation) {
            return operation.get();
        }
    }

    private static final class RollingBackApprovalTransaction implements ApplicationTransaction {
        private final InMemoryPlans plans;
        private final InMemoryApprovals approvals;

        private RollingBackApprovalTransaction(
                InMemoryPlans plans, InMemoryApprovals approvals) {
            this.plans = plans;
            this.approvals = approvals;
        }

        @Override
        public <T> T execute(Supplier<T> operation) {
            ActionPlanApproval before = approvals.byPlan.get(plans.current.id());
            try {
                return operation.get();
            } catch (RuntimeException failure) {
                approvals.byPlan.remove(plans.current.id());
                if (before != null) {
                    approvals.byPlan.put(plans.current.id(), before);
                }
                throw failure;
            }
        }
    }

    private static final class InMemoryApprovals implements ActionPlanApprovalRepository {
        private final Map<ActionPlanId, ActionPlanApproval> byPlan = new HashMap<>();
        private int saveCalls;

        @Override
        public Optional<ActionPlanApproval> findByPlanId(ActionPlanId planId) {
            return Optional.ofNullable(byPlan.get(planId));
        }

        @Override
        public Optional<ActionPlanApproval> findBySubjectAndRequestKey(
                String subject, ApprovalRequestKey requestKey) {
            return byPlan.values().stream()
                    .filter(value -> value.decidedBySubject().equals(subject)
                            && value.requestKey().equals(requestKey))
                    .findFirst();
        }

        @Override
        public ActionPlanApproval save(ActionPlanApproval approval) {
            saveCalls++;
            byPlan.put(approval.planId(), approval);
            return approval;
        }
    }
}
