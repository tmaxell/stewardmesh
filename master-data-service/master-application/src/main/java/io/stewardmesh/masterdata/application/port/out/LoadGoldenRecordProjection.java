package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.goldenrecord.SupplierAddress;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierParty;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierSite;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.Optional;

public interface LoadGoldenRecordProjection {

    Optional<SupplierParty> findParty(SupplierPartyId partyId);

    Optional<SupplierAddress> findAddress(SupplierAddressId addressId);

    Optional<SupplierSite> findSite(SupplierSiteId siteId);
}
