package io.stewardmesh.masterdata.domain.organization;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import java.time.LocalDate;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessUnitTest {

    @Test
    void exposesRoleOnlyWithinInclusiveBusinessDates() {
        var unit = unit(Set.of(BusinessUnitRole.PROCUREMENT), Optional.of(LocalDate.of(2026, 12, 31)));

        assertFalse(unit.supports(BusinessUnitRole.PROCUREMENT, LocalDate.of(2025, 12, 31)));
        assertTrue(unit.supports(BusinessUnitRole.PROCUREMENT, LocalDate.of(2026, 1, 1)));
        assertTrue(unit.supports(BusinessUnitRole.PROCUREMENT, LocalDate.of(2026, 12, 31)));
        assertFalse(unit.supports(BusinessUnitRole.CLIENT, LocalDate.of(2026, 6, 1)));
    }

    @Test
    void rejectsNonCanonicalCodeAndInvalidReferenceShape() {
        assertThrows(IllegalArgumentException.class, () -> new BusinessUnitCode("client bu"));
        assertThrows(IllegalArgumentException.class, () -> unit(Set.of(), Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> new BusinessUnit(
                new BusinessUnitId(UUID.randomUUID()),
                new BusinessUnitCode("CLIENT-A"),
                "Synthetic Client Unit",
                Set.of(BusinessUnitRole.CLIENT),
                0,
                LocalDate.of(2026, 1, 1),
                Optional.empty()));
        assertThrows(IllegalArgumentException.class, () -> unit(
                Set.of(BusinessUnitRole.CLIENT), Optional.of(LocalDate.of(2025, 12, 31))));
    }

    private static BusinessUnit unit(Set<BusinessUnitRole> roles, Optional<LocalDate> validTo) {
        return new BusinessUnit(
                new BusinessUnitId(UUID.fromString("10000000-0000-0000-0000-000000000001")),
                new BusinessUnitCode("CLIENT-A"),
                "Synthetic Client Unit",
                roles,
                1,
                LocalDate.of(2026, 1, 1),
                validTo);
    }
}
