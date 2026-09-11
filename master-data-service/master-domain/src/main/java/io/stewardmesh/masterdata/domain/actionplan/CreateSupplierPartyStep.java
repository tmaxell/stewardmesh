package io.stewardmesh.masterdata.domain.actionplan;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.util.List;
import java.util.Objects;

/** Creates a party from an exact immutable source assertion. */
public record CreateSupplierPartyStep(
        int sequence,
        SupplierPartyId partyId,
        SourceRecordIdentity sourceRecord,
        ActionReasonCode reasonCode,
        List<EvidenceReference> evidence)
        implements ActionPlanStep {

    public CreateSupplierPartyStep {
        sequence = ActionPlanStepSupport.requireSequence(sequence);
        Objects.requireNonNull(partyId, "partyId must not be null");
        Objects.requireNonNull(sourceRecord, "sourceRecord must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        evidence = ActionPlanStepSupport.requireEvidence(evidence);
    }

    @Override
    public ActionType type() {
        return ActionType.CREATE_SUPPLIER_PARTY;
    }
}
