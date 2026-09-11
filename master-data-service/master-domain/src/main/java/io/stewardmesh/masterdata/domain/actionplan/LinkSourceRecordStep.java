package io.stewardmesh.masterdata.domain.actionplan;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.util.List;
import java.util.Objects;

/** Links an immutable source assertion to an existing, version-matched party. */
public record LinkSourceRecordStep(
        int sequence,
        SourceRecordIdentity sourceRecord,
        SupplierPartyId partyId,
        long expectedPartyVersion,
        ActionReasonCode reasonCode,
        List<EvidenceReference> evidence)
        implements ActionPlanStep {

    public LinkSourceRecordStep {
        sequence = ActionPlanStepSupport.requireSequence(sequence);
        Objects.requireNonNull(sourceRecord, "sourceRecord must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        expectedPartyVersion = ActionPlanStepSupport.requireExpectedVersion(
                expectedPartyVersion, "party");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        evidence = ActionPlanStepSupport.requireEvidence(evidence);
    }

    @Override
    public ActionType type() {
        return ActionType.LINK_SOURCE_RECORD;
    }
}
