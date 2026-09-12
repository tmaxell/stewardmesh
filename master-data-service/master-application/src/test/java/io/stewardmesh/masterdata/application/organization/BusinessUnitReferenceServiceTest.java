package io.stewardmesh.masterdata.application.organization;

import io.stewardmesh.masterdata.application.DirectApplicationTransaction;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitCode;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitRole;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class BusinessUnitReferenceServiceTest {

    private static final BusinessUnitId ID =
            new BusinessUnitId(UUID.fromString("10000000-0000-0000-0000-000000000001"));

    @Test
    void synchronizesMonotonicVersionsAndReplaysIdenticalVersion() {
        var repository = new InMemoryBusinessUnits();
        var service = new BusinessUnitReferenceService(repository, new DirectApplicationTransaction());
        var first = unit(ID, "CLIENT-A", 1, "Synthetic Client A");
        var second = unit(ID, "CLIENT-A", 2, "Synthetic Client Alpha");

        assertEquals(first, service.execute(first));
        assertEquals(first, service.execute(first));
        assertEquals(second, service.execute(second));
        assertEquals(second, new BusinessUnitReadService(repository).execute(ID));
    }

    @Test
    void rejectsStaleConflictingAndReassignedReferenceIdentity() {
        var repository = new InMemoryBusinessUnits();
        var service = new BusinessUnitReferenceService(repository, new DirectApplicationTransaction());
        service.execute(unit(ID, "CLIENT-A", 2, "Synthetic Client A"));

        assertThrows(BusinessUnitConflictException.class,
                () -> service.execute(unit(ID, "CLIENT-A", 1, "Synthetic Client A")));
        assertThrows(BusinessUnitConflictException.class,
                () -> service.execute(unit(ID, "CLIENT-A", 2, "Changed at same version")));
        assertThrows(BusinessUnitConflictException.class,
                () -> service.execute(unit(ID, "CLIENT-B", 3, "Synthetic Client A")));
        assertThrows(BusinessUnitConflictException.class, () -> service.execute(unit(
                new BusinessUnitId(UUID.fromString("10000000-0000-0000-0000-000000000002")),
                "CLIENT-A", 3, "Other identity")));
        assertThrows(BusinessUnitNotFoundException.class, () -> new BusinessUnitReadService(repository).execute(
                new BusinessUnitId(UUID.fromString("10000000-0000-0000-0000-000000000099"))));
    }

    static BusinessUnit unit(BusinessUnitId id, String code, long version, String name) {
        return new BusinessUnit(
                id,
                new BusinessUnitCode(code),
                name,
                Set.of(BusinessUnitRole.CLIENT),
                version,
                LocalDate.of(2026, 1, 1),
                Optional.empty());
    }

    static final class InMemoryBusinessUnits implements BusinessUnitRepository {
        private final Map<BusinessUnitId, BusinessUnit> values = new HashMap<>();

        @Override
        public Optional<BusinessUnit> findById(BusinessUnitId id) {
            return Optional.ofNullable(values.get(id));
        }

        @Override
        public Optional<BusinessUnit> findByCode(BusinessUnitCode code) {
            return values.values().stream().filter(value -> value.code().equals(code)).findFirst();
        }

        @Override
        public BusinessUnit save(BusinessUnit businessUnit) {
            values.put(businessUnit.id(), businessUnit);
            return businessUnit;
        }
    }
}
