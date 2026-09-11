package io.stewardmesh.masterdata.domain.actionplan;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Current master-data facts a sealed plan refers to, read once so simulation stays a pure
 * calculation. Only entities the plan actually names belong here.
 */
public record SimulationSnapshot(
        Map<SupplierPartyId, Long> partyVersions,
        Map<SupplierSiteId, Long> siteVersions,
        Set<SupplierAddressId> addressIds,
        Map<BusinessUnitId, BusinessUnit> businessUnits,
        Set<SourceRecordIdentity> sourceRecords,
        List<SiteAssignment> assignments) {

    public SimulationSnapshot {
        partyVersions = Map.copyOf(Objects.requireNonNull(partyVersions, "partyVersions"));
        siteVersions = Map.copyOf(Objects.requireNonNull(siteVersions, "siteVersions"));
        addressIds = Set.copyOf(Objects.requireNonNull(addressIds, "addressIds"));
        businessUnits = Map.copyOf(Objects.requireNonNull(businessUnits, "businessUnits"));
        sourceRecords = Set.copyOf(Objects.requireNonNull(sourceRecords, "sourceRecords"));
        assignments = List.copyOf(Objects.requireNonNull(assignments, "assignments"));
    }

    public static SimulationSnapshot empty() {
        return new SimulationSnapshot(
                Map.of(), Map.of(), Set.of(), Map.of(), Set.of(), List.of());
    }
}
