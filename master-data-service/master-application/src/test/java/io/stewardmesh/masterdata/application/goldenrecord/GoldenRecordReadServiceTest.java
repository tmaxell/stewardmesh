package io.stewardmesh.masterdata.application.goldenrecord;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttribute;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeProvenance;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordProjector;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierParty;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRule;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GoldenRecordReadServiceTest {

    private static final UUID PARTY_ID = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @Test
    void mapsCurrentPartyProjectionWithoutLosingProvenance() {
        SupplierParty party = party();
        var service = new GoldenRecordReadService(new StubLoader(Optional.of(party)));

        GoldenRecordView view = service.execute(new GoldenRecordQuery(GoldenEntityType.PARTY, PARTY_ID));

        assertEquals(PARTY_ID, view.partyId());
        assertEquals(2, view.attributes().size());
        assertEquals(GoldenRecordProjector.RULESET_ID, view.attributes().getFirst().provenance().rulesetId());
    }

    @Test
    void reportsMissingTypedProjection() {
        var service = new GoldenRecordReadService(new StubLoader(Optional.empty()));

        assertThrows(GoldenRecordNotFoundException.class, () -> service.execute(
                new GoldenRecordQuery(GoldenEntityType.PARTY, PARTY_ID)));
    }

    private static SupplierParty party() {
        var source = new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC_ERP"), "source-1", 1);
        var association = new SourceAssociationId(UUID.fromString("30000000-0000-0000-0000-000000000001"));
        var decidedAt = Instant.parse("2026-09-11T08:00:00Z");
        var provenance = new GoldenAttributeProvenance(
                source, association, SurvivorshipRule.TRUSTED_SOURCE,
                GoldenRecordProjector.RULESET_ID, decidedAt);
        return new SupplierParty(
                new SupplierPartyId(PARTY_ID), new GoldenRecordVersion(1),
                GoldenRecordProjector.RULESET_ID, decidedAt,
                Map.of(
                        GoldenAttributeName.LEGAL_NAME,
                        new GoldenAttribute(GoldenAttributeName.LEGAL_NAME, "SYNTHETIC LLC", provenance),
                        GoldenAttributeName.INN,
                        new GoldenAttribute(GoldenAttributeName.INN, "9902000005", provenance)),
                List.of(association));
    }

    private record StubLoader(Optional<SupplierParty> party) implements LoadGoldenRecordProjection {
        @Override
        public Optional<SupplierParty> findParty(SupplierPartyId partyId) { return party; }
        @Override
        public Optional<io.stewardmesh.masterdata.domain.goldenrecord.SupplierAddress> findAddress(
                SupplierAddressId addressId) { return Optional.empty(); }
        @Override
        public Optional<io.stewardmesh.masterdata.domain.goldenrecord.SupplierSite> findSite(
                SupplierSiteId siteId) { return Optional.empty(); }
    }
}
