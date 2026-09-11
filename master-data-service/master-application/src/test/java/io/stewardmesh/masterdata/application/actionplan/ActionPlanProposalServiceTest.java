package io.stewardmesh.masterdata.application.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.ActionPlanIdentityGenerator;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionPlanProposalServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-11T14:30:00Z");
    private static final ActionPlanId PLAN_ID =
            new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000101"));

    @Test
    void sealsModelVisibleContentWithServerOwnedContext() {
        ActionPlanProposalService service = service();

        ActionPlan plan = service.propose(
                new ProposeActionPlanCommand(importId(), List.of(step())),
                new AuthenticatedProposalActor("oauth-subject-17"));

        assertEquals(PLAN_ID, plan.id());
        assertEquals(1, plan.version().value());
        assertEquals(NOW, plan.createdAt());
        assertEquals("oauth-subject-17", plan.proposedBySubject());
        assertEquals(importId(), plan.importId());
        assertNotNull(plan.hash());
    }

    @Test
    void commandDefensivelyCopiesStepsBeforeSealing() {
        List<io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep> mutable =
                new ArrayList<>(List.of(step()));
        ProposeActionPlanCommand command = new ProposeActionPlanCommand(importId(), mutable);
        mutable.clear();

        ActionPlan plan = service().propose(
                command, new AuthenticatedProposalActor("oauth-subject-17"));

        assertEquals(1, plan.steps().size());
        assertThrows(UnsupportedOperationException.class, () -> command.steps().clear());
    }

    @Test
    void rejectsMissingAuthenticatedActorAndInvalidPlanContent() {
        ProposeActionPlanCommand command =
                new ProposeActionPlanCommand(importId(), List.of(step()));

        assertThrows(NullPointerException.class, () -> service().propose(command, null));
        assertThrows(IllegalArgumentException.class, () -> new AuthenticatedProposalActor(" "));
        assertThrows(
                IllegalArgumentException.class,
                () -> service().propose(
                        new ProposeActionPlanCommand(importId(), List.of()),
                        new AuthenticatedProposalActor("oauth-subject-17")));
    }

    private static ActionPlanProposalService service() {
        ActionPlanIdentityGenerator identities = () -> PLAN_ID;
        return new ActionPlanProposalService(identities, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    private static CreateSupplierPartyStep step() {
        SourceRecordIdentity source =
                new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-17", 4);
        return new CreateSupplierPartyStep(
                1,
                new SupplierPartyId(
                        UUID.fromString("00000000-0000-0000-0000-000000000102")),
                source,
                new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                List.of(new EvidenceReference(
                        EvidenceType.SOURCE_RECORD, "erp-a:supplier-17", 4)));
    }

    private static ImportJobId importId() {
        return new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000103"));
    }
}
