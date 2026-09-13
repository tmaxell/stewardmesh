package io.stewardmesh.masterdata.domain.actionplan;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.List;
import java.util.Objects;

/** Creates an operational site under an existing, version-matched party. */
public record CreateSupplierSiteStep(
        int sequence,
        SupplierSiteId siteId,
        SupplierPartyId partyId,
        long expectedPartyVersion,
        SupplierAddressId addressId,
        BusinessUnitId procurementBusinessUnitId,
        ActionReasonCode reasonCode,
        List<EvidenceReference> evidence)
        implements ActionPlanStep {

    public CreateSupplierSiteStep {
        sequence = ActionPlanStepSupport.requireSequence(sequence);
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        expectedPartyVersion = ActionPlanStepSupport.requireExpectedVersion(
                expectedPartyVersion, "party");
        Objects.requireNonNull(addressId, "addressId must not be null");
        Objects.requireNonNull(
                procurementBusinessUnitId, "procurementBusinessUnitId must not be null");
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        evidence = ActionPlanStepSupport.requireEvidence(evidence);
    }

    @Override
    public ActionType type() {
        return ActionType.CREATE_SUPPLIER_SITE;
    }
}
