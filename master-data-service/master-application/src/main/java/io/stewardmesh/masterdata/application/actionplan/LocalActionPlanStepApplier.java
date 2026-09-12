package io.stewardmesh.masterdata.application.actionplan;

import io.stewardmesh.masterdata.application.identity.IdentityResolutionKey;
import io.stewardmesh.masterdata.application.port.in.AssignSupplierSite;
import io.stewardmesh.masterdata.application.port.in.ProjectSourceRecord;
import io.stewardmesh.masterdata.application.port.out.ActionPlanStepApplier;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.domain.actionplan.ActionPlanStep;
import io.stewardmesh.masterdata.domain.actionplan.AssignSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierPartyStep;
import io.stewardmesh.masterdata.domain.actionplan.CreateSupplierSiteStep;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceReference;
import io.stewardmesh.masterdata.domain.actionplan.EvidenceType;
import io.stewardmesh.masterdata.domain.actionplan.LinkSourceRecordStep;
import io.stewardmesh.masterdata.domain.identity.MatchRulesetId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import java.util.Objects;

/**
 * Applies plan steps through existing master-data use cases. Source-bearing steps name the exact
 * versioned match ruleset through their `MATCH_EVALUATION` evidence reference. A site step verifies
 * the site materialized by the earlier source projection in the same sealed plan.
 */
public final class LocalActionPlanStepApplier implements ActionPlanStepApplier {

    private final ProjectSourceRecord projectSourceRecord;
    private final LoadGoldenRecordProjection goldenRecords;
    private final AssignSupplierSite assignSupplierSite;

    public LocalActionPlanStepApplier(
            ProjectSourceRecord projectSourceRecord,
            LoadGoldenRecordProjection goldenRecords,
            AssignSupplierSite assignSupplierSite) {
        this.projectSourceRecord = Objects.requireNonNull(
                projectSourceRecord, "project source record must not be null");
        this.goldenRecords = Objects.requireNonNull(
                goldenRecords, "golden records must not be null");
        this.assignSupplierSite = Objects.requireNonNull(
                assignSupplierSite, "assign supplier site must not be null");
    }

    @Override
    public AppliedActionStep apply(ActionPlanStep step) {
        Objects.requireNonNull(step, "step must not be null");
        return switch (step) {
            case CreateSupplierPartyStep create -> createParty(create);
            case LinkSourceRecordStep link -> linkSource(link);
            case CreateSupplierSiteStep create -> createSite(create);
            case AssignSupplierSiteStep assign -> assignSite(assign);
        };
    }

    private AppliedActionStep createParty(CreateSupplierPartyStep step) {
        projectSourceRecord.execute(new IdentityResolutionKey(
                step.sourceRecord(), matchRuleset(step)));
        var party = goldenRecords.findParty(step.partyId()).orElseThrow(() ->
                failure("source projection did not create the planned supplier party"));
        return new AppliedActionStep(
                step.sequence(), step.type(), "SUPPLIER_PARTY", step.partyId().value(),
                party.version().value(), "SupplierCreated");
    }

    private AppliedActionStep linkSource(LinkSourceRecordStep step) {
        projectSourceRecord.execute(new IdentityResolutionKey(
                step.sourceRecord(), matchRuleset(step)));
        var party = goldenRecords.findParty(step.partyId()).orElseThrow(() ->
                failure("source projection did not update the planned supplier party"));
        if (party.version().value() != step.expectedPartyVersion() + 1) {
            throw failure("source projection produced an unexpected supplier party version");
        }
        return new AppliedActionStep(
                step.sequence(), step.type(), "SUPPLIER_PARTY", step.partyId().value(),
                party.version().value(), "SupplierGoldenRecordChanged");
    }

    private AppliedActionStep createSite(CreateSupplierSiteStep step) {
        var site = goldenRecords.findSite(step.siteId()).orElseThrow(() ->
                failure("the planned supplier site was not materialized by source projection"));
        if (!site.partyId().equals(step.partyId()) || !site.addressId().equals(step.addressId())) {
            throw failure("materialized supplier site does not match the sealed plan");
        }
        return new AppliedActionStep(
                step.sequence(), step.type(), "SUPPLIER_SITE", step.siteId().value(),
                site.version().value(), "SupplierSiteCreated");
    }

    private AppliedActionStep assignSite(AssignSupplierSiteStep step) {
        SiteAssignment stored = assignSupplierSite.execute(new SiteAssignment(
                step.assignmentId(), step.siteId(), step.clientBusinessUnitId(), step.purposes(),
                step.validFrom(), step.validTo(), 1));
        return new AppliedActionStep(
                step.sequence(), step.type(), "SITE_ASSIGNMENT", stored.id().value(),
                stored.version(), "SupplierSiteAssigned");
    }

    private static MatchRulesetId matchRuleset(ActionPlanStep step) {
        var evidence = step.evidence().stream()
                .filter(reference -> reference.type() == EvidenceType.MATCH_EVALUATION)
                .toList();
        if (evidence.size() != 1) {
            throw failure("source mutation requires exactly one match-evaluation evidence reference");
        }
        EvidenceReference reference = evidence.getFirst();
        try {
            return new MatchRulesetId(reference.reference());
        } catch (IllegalArgumentException invalidRuleset) {
            throw failure("match-evaluation evidence must identify a versioned ruleset");
        }
    }

    private static ActionPlanStepApplyException failure(String message) {
        return new ActionPlanStepApplyException(message);
    }
}
