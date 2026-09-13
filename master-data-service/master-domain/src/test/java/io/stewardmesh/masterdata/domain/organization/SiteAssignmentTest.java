package io.stewardmesh.masterdata.domain.organization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SiteAssignmentTest {

    private static final SupplierSiteId SITE =
            new SupplierSiteId(UUID.fromString("20000000-0000-0000-0000-000000000001"));
    private static final BusinessUnitId CLIENT =
            new BusinessUnitId(UUID.fromString("10000000-0000-0000-0000-000000000001"));

    @Test
    void evaluatesInclusiveValidityAndPurposeSpecificConflicts() {
        var current = assignment("30000000-0000-0000-0000-000000000001", SitePurpose.PURCHASING,
                LocalDate.of(2026, 1, 1), Optional.of(LocalDate.of(2026, 6, 30)));
        var touching = assignment("30000000-0000-0000-0000-000000000002", SitePurpose.PURCHASING,
                LocalDate.of(2026, 6, 30), Optional.empty());
        var later = assignment("30000000-0000-0000-0000-000000000003", SitePurpose.PURCHASING,
                LocalDate.of(2026, 7, 1), Optional.empty());
        var pay = assignment("30000000-0000-0000-0000-000000000004", SitePurpose.PAY,
                LocalDate.of(2026, 6, 1), Optional.empty());

        assertTrue(current.isEffectiveOn(LocalDate.of(2026, 1, 1)));
        assertTrue(current.isEffectiveOn(LocalDate.of(2026, 6, 30)));
        assertFalse(current.isEffectiveOn(LocalDate.of(2026, 7, 1)));
        assertTrue(current.conflictsWith(touching));
        assertFalse(current.conflictsWith(later));
        assertFalse(current.conflictsWith(pay));
    }

    @Test
    void policyRejectsDuplicateIdentityAndOverlappingAuthorization() {
        var policy = new SiteAssignmentPolicy();
        var current = assignment("30000000-0000-0000-0000-000000000001", SitePurpose.PURCHASING,
                LocalDate.of(2026, 1, 1), Optional.empty());
        var overlap = assignment("30000000-0000-0000-0000-000000000002", SitePurpose.PURCHASING,
                LocalDate.of(2026, 2, 1), Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> policy.validate(current, List.of(current)));
        assertThrows(IllegalArgumentException.class, () -> policy.validate(overlap, List.of(current)));
    }

    @Test
    void rejectsInvalidIntervalsPurposesAndVersions() {
        assertThrows(IllegalArgumentException.class, () -> new SiteAssignment(
                new SiteAssignmentId(UUID.randomUUID()), SITE, CLIENT, Set.of(),
                LocalDate.of(2026, 1, 1), Optional.empty(), 1));
        assertThrows(IllegalArgumentException.class, () -> new SiteAssignment(
                new SiteAssignmentId(UUID.randomUUID()), SITE, CLIENT, Set.of(SitePurpose.PAY),
                LocalDate.of(2026, 2, 1), Optional.of(LocalDate.of(2026, 1, 31)), 1));
        assertThrows(IllegalArgumentException.class, () -> new SiteAssignment(
                new SiteAssignmentId(UUID.randomUUID()), SITE, CLIENT, Set.of(SitePurpose.PAY),
                LocalDate.of(2026, 1, 1), Optional.empty(), 0));
    }

    private static SiteAssignment assignment(
            String id, SitePurpose purpose, LocalDate validFrom, Optional<LocalDate> validTo) {
        return new SiteAssignment(
                new SiteAssignmentId(UUID.fromString(id)),
                SITE,
                CLIENT,
                Set.of(purpose),
                validFrom,
                validTo,
                1);
    }
}
