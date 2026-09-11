package io.stewardmesh.masterdata.application.goldenrecord;

import io.stewardmesh.masterdata.application.port.in.GetGoldenRecord;
import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierAddress;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierParty;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierSite;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.Objects;

public final class GoldenRecordReadService implements GetGoldenRecord {

    private final LoadGoldenRecordProjection projections;

    public GoldenRecordReadService(LoadGoldenRecordProjection projections) {
        this.projections = Objects.requireNonNull(projections, "projections must not be null");
    }

    @Override
    public GoldenRecordView execute(GoldenRecordQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        return switch (query.entityType()) {
            case PARTY -> projections.findParty(new SupplierPartyId(query.entityId()))
                    .map(GoldenRecordReadService::view)
                    .orElseThrow(GoldenRecordNotFoundException::new);
            case ADDRESS -> projections.findAddress(new SupplierAddressId(query.entityId()))
                    .map(GoldenRecordReadService::view)
                    .orElseThrow(GoldenRecordNotFoundException::new);
            case SITE -> projections.findSite(new SupplierSiteId(query.entityId()))
                    .map(GoldenRecordReadService::view)
                    .orElseThrow(GoldenRecordNotFoundException::new);
        };
    }

    private static GoldenRecordView view(SupplierParty party) {
        return new GoldenRecordView(
                io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType.PARTY,
                party.id().value(), party.id().value(), null, party.version().value(),
                party.rulesetId(), party.projectedAt(), party.attributes().values().stream().toList(),
                party.sourceAssociations());
    }

    private static GoldenRecordView view(SupplierAddress address) {
        return new GoldenRecordView(
                io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType.ADDRESS,
                address.id().value(), address.partyId().value(), address.id().value(), address.version().value(),
                address.rulesetId(), address.projectedAt(), address.attributes().values().stream().toList(),
                address.sourceAssociations());
    }

    private static GoldenRecordView view(SupplierSite site) {
        return new GoldenRecordView(
                io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType.SITE,
                site.id().value(), site.partyId().value(), site.addressId().value(), site.version().value(),
                site.rulesetId(), site.projectedAt(), site.attributes().values().stream().toList(),
                site.sourceAssociations());
    }
}
