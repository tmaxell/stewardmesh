package io.stewardmesh.masterdata.domain.actionplan;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import java.time.LocalDate;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Authorizes a client business unit to use an existing, version-matched supplier site. */
public record AssignSupplierSiteStep(
        int sequence,
        SiteAssignmentId assignmentId,
        SupplierSiteId siteId,
        long expectedSiteVersion,
        BusinessUnitId clientBusinessUnitId,
        Set<SitePurpose> purposes,
        LocalDate validFrom,
        Optional<LocalDate> validTo,
        ActionReasonCode reasonCode,
        List<EvidenceReference> evidence)
        implements ActionPlanStep {

    public AssignSupplierSiteStep {
        sequence = ActionPlanStepSupport.requireSequence(sequence);
        Objects.requireNonNull(assignmentId, "assignmentId must not be null");
        Objects.requireNonNull(siteId, "siteId must not be null");
        expectedSiteVersion = ActionPlanStepSupport.requireExpectedVersion(
                expectedSiteVersion, "site");
        Objects.requireNonNull(clientBusinessUnitId, "clientBusinessUnitId must not be null");
        Objects.requireNonNull(purposes, "purposes must not be null");
        if (purposes.isEmpty()) {
            throw new IllegalArgumentException("purposes must not be empty");
        }
        purposes = Collections.unmodifiableSet(EnumSet.copyOf(purposes));
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        validTo = Objects.requireNonNull(validTo, "validTo must not be null");
        if (validTo.isPresent() && validTo.orElseThrow().isBefore(validFrom)) {
            throw new IllegalArgumentException("validTo must not be before validFrom");
        }
        Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        evidence = ActionPlanStepSupport.requireEvidence(evidence);
    }

    @Override
    public ActionType type() {
        return ActionType.ASSIGN_SUPPLIER_SITE;
    }
}
