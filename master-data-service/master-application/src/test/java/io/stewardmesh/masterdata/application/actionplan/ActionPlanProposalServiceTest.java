package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.application.DirectApplicationTransaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.ActionPlanIdentityGenerator;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionPlanProposalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T14:30:00Z");
    private static final ActionPlanId PLAN_ID =
            new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000101"));
    private static final ActionPlanId SECOND_PLAN_ID =
            new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000104"));

    private final InMemoryActionPlanRepository plans = new InMemoryActionPlanRepository();

    @Test
    void sealsModelVisibleContentWithServerOwnedContextAndStoresItProposed() {
        GovernedActionPlan governed = service().propose(
                new ProposeActionPlanCommand(importId(), List.of(step())),
                new AuthenticatedProposalActor("oauth-subject-17"));

        assertEquals(PLAN_ID, governed.id());
        assertEquals(ActionPlanStatus.PROPOSED, governed.status());
        assertEquals(1, governed.version().value());
        assertEquals(NOW, governed.plan().createdAt());
        assertEquals("oauth-subject-17", governed.plan().proposedBySubject());
        assertEquals(importId(), governed.plan().importId());
        assertNotNull(governed.hash());
        assertEquals(governed, plans.findById(PLAN_ID).orElseThrow());
        assertEquals(1, plans.saveCount());
    }

    @Test
    void returnsTheUndecidedPlanInsteadOfSealingTheSameContentTwice() {
        ActionPlanProposalService service = service();
        var command = new ProposeActionPlanCommand(importId(), List.of(step()));

        GovernedActionPlan first =
                service.propose(command, new AuthenticatedProposalActor("oauth-subject-17"));
        GovernedActionPlan repeated =
                service.propose(command, new AuthenticatedProposalActor("oauth-subject-42"));

        assertEquals(first, repeated);
        assertEquals("oauth-subject-17", repeated.plan().proposedBySubject());
        assertEquals(1, plans.size());
        assertEquals(1, plans.saveCount());
    }

    @Test
    void sealsAFreshPlanOnceTheStoredOneLeavesTheProposedState() {
        ActionPlanProposalService service = service();
        var command = new ProposeActionPlanCommand(importId(), List.of(step()));

        GovernedActionPlan first =
                service.propose(command, new AuthenticatedProposalActor("oauth-subject-17"));
        plans.replace(first.transitionTo(ActionPlanStatus.REJECTED));

        GovernedActionPlan reproposed =
                service.propose(command, new AuthenticatedProposalActor("oauth-subject-17"));

        assertEquals(SECOND_PLAN_ID, reproposed.id());
        assertEquals(ActionPlanStatus.PROPOSED, reproposed.status());
        assertEquals(first.fingerprint(), reproposed.fingerprint());
        assertNotEquals(first.hash(), reproposed.hash());
        assertEquals(2, plans.size());
    }

    @Test
    void differentContentSealsSeparatePlans() {
        ActionPlanProposalService service = service();

        GovernedActionPlan first = service.propose(
                new ProposeActionPlanCommand(importId(), List.of(step())),
                new AuthenticatedProposalActor("oauth-subject-17"));
        GovernedActionPlan second = service.propose(
                new ProposeActionPlanCommand(importId(), List.of(step(9))),
                new AuthenticatedProposalActor("oauth-subject-17"));

        assertNotEquals(first.fingerprint(), second.fingerprint());
        assertEquals(2, plans.size());
        assertEquals(2, plans.saveCount());
    }

    @Test
    void commandDefensivelyCopiesStepsBeforeSealing() {
        List<ActionPlanStep> mutable = new ArrayList<>(List.of(step()));
        ProposeActionPlanCommand command = new ProposeActionPlanCommand(importId(), mutable);
        mutable.clear();

        GovernedActionPlan governed =
                service().propose(command, new AuthenticatedProposalActor("oauth-subject-17"));

        assertEquals(1, governed.plan().steps().size());
        assertThrows(UnsupportedOperationException.class, () -> command.steps().clear());
    }

    @Test
    void rejectsMissingAuthenticatedActorAndInvalidPlanContent() {
        ProposeActionPlanCommand command =
                new ProposeActionPlanCommand(importId(), List.of(step()));

        assertThrows(NullPointerException.class, () -> service().propose(command, null));
        assertThrows(NullPointerException.class, () -> service().propose(
                null, new AuthenticatedProposalActor("oauth-subject-17")));
        assertThrows(IllegalArgumentException.class, () -> new AuthenticatedProposalActor(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> service().propose(
                        new ProposeActionPlanCommand(importId(), List.of()),
                        new AuthenticatedProposalActor("oauth-subject-17")));
        assertEquals(0, plans.size());
    }

    private ActionPlanProposalService service() {
        Deque<ActionPlanId> identities = new ArrayDeque<>(List.of(PLAN_ID, SECOND_PLAN_ID));
        ActionPlanIdentityGenerator generator = identities::removeFirst;
        ApplicationTransaction transaction = new DirectApplicationTransaction();
        return new ActionPlanProposalService(
                plans, generator, transaction, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CreateSupplierPartyStep step() {
        return step(4);
    }

    private static CreateSupplierPartyStep step(long sourceVersion) {
        SourceRecordIdentity source = new SourceRecordIdentity(
                new SourceSystemRef("erp-a"), "supplier-17", sourceVersion);
        return new CreateSupplierPartyStep(
                1,
                new SupplierPartyId(UUID.fromString("00000000-0000-0000-0000-000000000102")),
                source,
                new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                List.of(new EvidenceReference(
                        EvidenceType.SOURCE_RECORD, "erp-a:supplier-17", sourceVersion)));
    }

    private static ImportJobId importId() {
        return new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000103"));
    }
}
