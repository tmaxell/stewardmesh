package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.identity.PartyMatchProfile;
import io.stewardmesh.masterdata.domain.identity.SiteMatchProfile;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.util.List;
import java.util.Set;

public interface LoadMatchProfiles {

    List<PartyMatchProfile> loadParties(Set<SupplierPartyId> partyIds);

    List<SiteMatchProfile> loadSites(Set<SupplierSiteId> siteIds);
}
