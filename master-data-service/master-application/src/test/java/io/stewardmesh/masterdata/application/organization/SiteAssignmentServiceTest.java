package io.stewardmesh.masterdata.application.organization;

import io.stewardmesh.masterdata.application.DirectApplicationTransaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.LoadGoldenRecordProjection;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttribute;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeName;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttributeProvenance;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierAddress;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierParty;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierSite;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRule;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRulesetId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.intake.SourceSystemRef;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitCode;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitRole;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentPolicy;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SiteAssignmentServiceTest {

    private static final SupplierSiteId SITE =
            new SupplierSiteId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final BusinessUnitId CLIENT =
            new BusinessUnitId(UUID.fromString("10000000-0000-0000-0000-000000000001"));

    @Test
    void assignsExistingSiteToEligibleClientAndReplaysIdentity() {
        var assignments = new InMemoryAssignments();
        var service = service(assignments, true, client(Set.of(BusinessUnitRole.CLIENT), Optional.empty()));
        var candidate = assignment("30000000-0000-0000-0000-000000000001", 1,
                LocalDate.of(2026, 2, 1), Optional.empty());

        assertEquals(candidate, service.execute(candidate));
        assertEquals(candidate, service.execute(candidate));
        assertEquals(List.of(candidate),
                new SiteAssignmentReadService(assignments)
                        .execute(new SiteAssignmentQuery(SITE, CLIENT, 10)));
    }

    @Test
    void rejectsMissingSiteIneligibleClientAndInvalidInitialVersion() {
        var candidate = assignment("30000000-0000-0000-0000-000000000001", 1,
                LocalDate.of(2026, 2, 1), Optional.empty());

        assertThrows(SiteAssignmentConflictException.class,
                () -> service(new InMemoryAssignments(), false,
                        client(Set.of(BusinessUnitRole.CLIENT), Optional.empty())).execute(candidate));
        assertThrows(SiteAssignmentConflictException.class,
                () -> service(new InMemoryAssignments(), true,
                        client(Set.of(BusinessUnitRole.PROCUREMENT), Optional.empty())).execute(candidate));
        assertThrows(SiteAssignmentConflictException.class,
                () -> service(new InMemoryAssignments(), true,
                        client(Set.of(BusinessUnitRole.CLIENT), Optional.of(LocalDate.of(2026, 6, 30))))
                        .execute(assignment("30000000-0000-0000-0000-000000000002", 1,
                                LocalDate.of(2026, 2, 1), Optional.empty())));
        assertThrows(SiteAssignmentConflictException.class,
                () -> service(new InMemoryAssignments(), true,
                        client(Set.of(BusinessUnitRole.CLIENT), Optional.empty()))
                        .execute(assignment("30000000-0000-0000-0000-000000000003", 2,
                                LocalDate.of(2026, 2, 1), Optional.empty())));
    }

    @Test
    void rejectsOverlappingAndConflictingReplay() {
        var assignments = new InMemoryAssignments();
        var service = service(assignments, true, client(Set.of(BusinessUnitRole.CLIENT), Optional.empty()));
        service.execute(assignment("30000000-0000-0000-0000-000000000001", 1,
                LocalDate.of(2026, 1, 1), Optional.empty()));

        assertThrows(SiteAssignmentConflictException.class, () -> service.execute(assignment(
                "30000000-0000-0000-0000-000000000002", 1,
                LocalDate.of(2026, 2, 1), Optional.empty())));
        assertThrows(SiteAssignmentConflictException.class, () -> service.execute(new SiteAssignment(
                new SiteAssignmentId(UUID.fromString("30000000-0000-0000-0000-000000000001")),
                SITE, CLIENT, Set.of(SitePurpose.PAY), LocalDate.of(2026, 1, 1), Optional.empty(), 1)));
    }

    private static SiteAssignmentService service(
            InMemoryAssignments assignments, boolean siteExists, BusinessUnit client) {
        var units = new BusinessUnitReferenceServiceTest.InMemoryBusinessUnits();
        units.save(client);
        return new SiteAssignmentService(
                goldenRecords(siteExists),
                units,
                assignments,
                new DirectApplicationTransaction(),
                new SiteAssignmentPolicy());
    }

    private static BusinessUnit client(Set<BusinessUnitRole> roles, Optional<LocalDate> validTo) {
        return new BusinessUnit(
                CLIENT,
                new BusinessUnitCode("CLIENT-A"),
                "Synthetic Client Unit",
                roles,
                1,
                LocalDate.of(2026, 1, 1),
                validTo);
    }

    private static SiteAssignment assignment(
            String id, long version, LocalDate validFrom, Optional<LocalDate> validTo) {
        return new SiteAssignment(
                new SiteAssignmentId(UUID.fromString(id)), SITE, CLIENT,
                Set.of(SitePurpose.PURCHASING), validFrom, validTo, version);
    }

    private static LoadGoldenRecordProjection goldenRecords(boolean siteExists) {
        return new LoadGoldenRecordProjection() {
            @Override
            public Optional<SupplierParty> findParty(SupplierPartyId partyId) {
                return Optional.empty();
            }

            @Override
            public Optional<SupplierAddress> findAddress(SupplierAddressId addressId) {
                return Optional.empty();
            }

            @Override
            public Optional<SupplierSite> findSite(SupplierSiteId siteId) {
                return siteExists && SITE.equals(siteId) ? Optional.of(site()) : Optional.empty();
            }
        };
    }

    private static SupplierSite site() {
        var associationId = new SourceAssociationId(
                UUID.fromString("40000000-0000-0000-0000-000000000001"));
        var ruleset = new SurvivorshipRulesetId("supplier-survivorship-v1");
        var decidedAt = Instant.parse("2026-01-01T00:00:00Z");
        var provenance = new GoldenAttributeProvenance(
                new SourceRecordIdentity(new SourceSystemRef("SYNTHETIC"), "SITE-1", 1),
                associationId,
                SurvivorshipRule.TRUSTED_SOURCE,
                ruleset,
                decidedAt);
        return new SupplierSite(
                SITE,
                new SupplierPartyId(UUID.fromString("50000000-0000-0000-0000-000000000001")),
                new SupplierAddressId(UUID.fromString("60000000-0000-0000-0000-000000000001")),
                new GoldenRecordVersion(1),
                ruleset,
                decidedAt,
                java.util.Map.of(
                        GoldenAttributeName.KPP,
                        new GoldenAttribute(GoldenAttributeName.KPP, "990101001", provenance)),
                List.of(associationId));
    }

    private static final class InMemoryAssignments implements SiteAssignmentRepository {
        private final List<SiteAssignment> values = new ArrayList<>();

        @Override
        public Optional<SiteAssignment> findById(SiteAssignmentId id) {
            return values.stream().filter(value -> value.id().equals(id)).findFirst();
        }

        @Override
        public List<SiteAssignment> findForSiteAndClient(
                SupplierSiteId siteId, BusinessUnitId clientBusinessUnitId, int limit) {
            return values.stream()
                    .filter(value -> value.siteId().equals(siteId))
                    .filter(value -> value.clientBusinessUnitId().equals(clientBusinessUnitId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public SiteAssignment save(SiteAssignment assignment) {
            values.add(assignment);
            return assignment;
        }
    }
}
