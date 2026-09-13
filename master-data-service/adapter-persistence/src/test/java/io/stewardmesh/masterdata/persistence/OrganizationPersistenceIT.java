package io.stewardmesh.masterdata.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.organization.OrganizationWriteException;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.application.port.out.SiteAssignmentRepository;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitCode;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitRole;
import io.stewardmesh.masterdata.domain.organization.SiteAssignment;
import io.stewardmesh.masterdata.domain.organization.SiteAssignmentId;
import io.stewardmesh.masterdata.domain.organization.SitePurpose;
import io.stewardmesh.masterdata.persistence.jpa.IntakePersistenceConfiguration;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = OrganizationPersistenceIT.TestApplication.class)
class OrganizationPersistenceIT extends PostgreSqlIntegrationTestSupport {

    @Autowired
    private BusinessUnitRepository businessUnits;

    @Autowired
    private SiteAssignmentRepository assignments;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @DynamicPropertySource
    static void postgresProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
    }

    @Test
    void storesCurrentBusinessUnitAndImmutableReferenceVersions() {
        var id = new BusinessUnitId(UUID.randomUUID());
        var first = businessUnit(id, "CLIENT-" + suffix(id), 1, "Synthetic Client One");
        var second = businessUnit(id, first.code().value(), 2, "Synthetic Client Two");

        businessUnits.save(first);
        businessUnits.save(second);

        assertEquals(second, businessUnits.findById(id).orElseThrow());
        assertEquals(second, businessUnits.findByCode(second.code()).orElseThrow());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM business_unit_version WHERE business_unit_id = ?",
                Integer.class,
                id.value()));
        assertThrows(DataAccessException.class, () -> jdbcTemplate.update(
                "UPDATE business_unit_version SET display_name = 'Changed' WHERE business_unit_id = ?",
                id.value()));
    }

    @Test
    void roundTripsAssignmentAndRejectsOverlappingPurpose() {
        var siteId = new SupplierSiteId(UUID.randomUUID());
        insertSite(siteId);
        var clientId = new BusinessUnitId(UUID.randomUUID());
        businessUnits.save(businessUnit(
                clientId, "CLIENT-" + suffix(clientId), 1, "Synthetic Assignment Client"));
        var first = assignment(
                UUID.randomUUID(), siteId, clientId, LocalDate.of(2026, 1, 1),
                Optional.of(LocalDate.of(2026, 6, 30)), Set.of(SitePurpose.PURCHASING));

        assignments.save(first);

        assertEquals(first, assignments.findById(first.id()).orElseThrow());
        assertEquals(
                java.util.List.of(first),
                assignments.findForSiteAndClient(siteId, clientId, 10));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM site_assignment_version WHERE assignment_id = ?",
                Integer.class,
                first.id().value()));

        var overlap = assignment(
                UUID.randomUUID(), siteId, clientId, LocalDate.of(2026, 6, 30),
                Optional.empty(), Set.of(SitePurpose.PURCHASING, SitePurpose.PAY));
        assertEquals(java.util.List.of(first), assignments.findConflicts(overlap, 1));
        assertThrows(OrganizationWriteException.class, () -> assignments.save(overlap));

        var independentPurpose = assignment(
                UUID.randomUUID(), siteId, clientId, LocalDate.of(2026, 6, 30),
                Optional.empty(), Set.of(SitePurpose.PAY));
        assignments.save(independentPurpose);
        assertEquals(2, assignments.findForSiteAndClient(siteId, clientId, 10).size());
        assertEquals(
                java.util.List.of(independentPurpose),
                assignments.findConflicts(
                        assignment(
                                UUID.randomUUID(),
                                siteId,
                                clientId,
                                LocalDate.of(2027, 1, 1),
                                Optional.empty(),
                                Set.of(SitePurpose.PAY)),
                        10));
    }

    @Test
    void databaseRejectsUnknownSiteOrBusinessUnit() {
        var unknownSite = new SupplierSiteId(UUID.randomUUID());
        var unknownClient = new BusinessUnitId(UUID.randomUUID());
        var candidate = assignment(
                UUID.randomUUID(), unknownSite, unknownClient, LocalDate.of(2026, 1, 1),
                Optional.empty(), Set.of(SitePurpose.PAY));

        assertThrows(OrganizationWriteException.class, () -> assignments.save(candidate));
    }

    private void insertSite(SupplierSiteId siteId) {
        jdbcTemplate.update(
                """
                INSERT INTO golden_record_metadata
                    (entity_type, entity_id, party_id, address_id, projection_version,
                     survivorship_ruleset, projected_at, lock_version)
                VALUES ('SITE', ?, ?, ?, 1, 'supplier-survivorship-v1', CURRENT_TIMESTAMP, 0)
                """,
                siteId.value(), UUID.randomUUID(), UUID.randomUUID());
    }

    private static BusinessUnit businessUnit(
            BusinessUnitId id, String code, long version, String name) {
        return new BusinessUnit(
                id,
                new BusinessUnitCode(code),
                name,
                Set.of(BusinessUnitRole.CLIENT),
                version,
                LocalDate.of(2026, 1, 1),
                Optional.empty());
    }

    private static SiteAssignment assignment(
            UUID id,
            SupplierSiteId siteId,
            BusinessUnitId clientId,
            LocalDate validFrom,
            Optional<LocalDate> validTo,
            Set<SitePurpose> purposes) {
        return new SiteAssignment(
                new SiteAssignmentId(id), siteId, clientId, purposes, validFrom, validTo, 1);
    }

    private static String suffix(BusinessUnitId id) {
        return id.value().toString().substring(0, 8).toUpperCase(java.util.Locale.ROOT);
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    @Import(IntakePersistenceConfiguration.class)
    static class TestApplication {}
}
