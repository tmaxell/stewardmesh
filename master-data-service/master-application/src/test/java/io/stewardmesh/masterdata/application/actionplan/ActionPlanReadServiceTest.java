package io.stewardmesh.masterdata.application.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.domain.actionplan.ActionPlan;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanId;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStatus;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanVersion;
import io.stewardmesh.masterdata.domain.actionplan.ActionReasonCode;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.GovernedActionPlan;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ActionPlanReadServiceTest {

    private static final ActionPlanId PLAN_ID =
            new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000301"));

    private final InMemoryActionPlanRepository plans = new InMemoryActionPlanRepository();

    @Test
    void returnsTheStoredPlanWithItsGovernedStatus() {
        GovernedActionPlan approved =
                GovernedActionPlan.proposed(plan()).transitionTo(ActionPlanStatus.APPROVED);
        plans.replace(approved);

        GovernedActionPlan found = new ActionPlanReadService(plans).execute(PLAN_ID);

        assertEquals(approved, found);
        assertEquals(ActionPlanStatus.APPROVED, found.status());
    }

    @Test
    void reportsAnUnknownPlanAndRejectsMissingIdentity() {
        var service = new ActionPlanReadService(plans);
        var unknown = new ActionPlanId(UUID.fromString("00000000-0000-0000-0000-000000000302"));

        assertThrows(ActionPlanNotFoundException.class, () -> service.execute(unknown));
        assertThrows(NullPointerException.class, () -> service.execute(null));
        assertThrows(NullPointerException.class, () -> new ActionPlanReadService(null));
    }

    private static ActionPlan plan() {
        SourceRecordIdentity source =
                new SourceRecordIdentity(new SourceSystemRef("erp-a"), "supplier-31", 2);
        return ActionPlan.propose(
                PLAN_ID,
                ActionPlanVersion.initial(),
                new ImportJobId(UUID.fromString("00000000-0000-0000-0000-000000000303")),
                Instant.parse("2026-09-12T09:00:00Z"),
                "oauth-subject-31",
                List.of(new CreateSupplierPartyStep(
                        1,
                        new SupplierPartyId(
                                UUID.fromString("00000000-0000-0000-0000-000000000304")),
                        source,
                        new ActionReasonCode("NO_MATCH_NEW_PARTY"),
                        List.of(new EvidenceReference(
                                EvidenceType.SOURCE_RECORD, "erp-a:supplier-31", 2)))));
    }
}
