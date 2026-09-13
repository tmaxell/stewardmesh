package io.stewardmesh.masterdata.domain.actionplan;

import java.util.List;

/** Immutable, evidence-bearing mutation proposed for later simulation and approval. */
public sealed interface ActionPlanStep
        permits CreateSupplierPartyStep,
                LinkSourceRecordStep,
                CreateSupplierSiteStep,
                AssignSupplierSiteStep {

    int sequence();

    ActionReasonCode reasonCode();

    List<EvidenceReference> evidence();

    ActionType type();

    default ActionRisk risk() {
        return type().risk();
    }
}
