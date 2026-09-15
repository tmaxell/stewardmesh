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
    void decidesEveryRejectionBeforeOpeningTheWriteTransaction() {
        // The inbox consumer records a rejection in its own transaction. If a rejection were raised
        // from inside a nested transaction template, that template would mark the consumer's
        // transaction rollback-only and the quarantine record could never commit, so the event
        // would be neither applied nor recorded.
        var repository = new InMemoryBusinessUnits();
        var transaction = new CountingTransaction();
        var service = new BusinessUnitReferenceService(repository, transaction);
        service.execute(unit(ID, "CLIENT-A", 1, "Synthetic Client A"));
        int writesSoFar = transaction.opened;

        var reassigned = unit(
                new BusinessUnitId(UUID.fromString("10000000-0000-0000-0000-000000000009")),
                "CLIENT-A",
                1,
                "Synthetic Client A");
        assertThrows(BusinessUnitConflictException.class, () -> service.execute(reassigned));
        assertThrows(
                BusinessUnitConflictException.class,
                () -> service.execute(unit(ID, "CLIENT-A", 1, "Synthetic Client Renamed")));

        assertEquals(writesSoFar, transaction.opened, "a rejected event must open no transaction");
    }

    @Test
    void repeatsAnIdenticalVersionWithoutOpeningAWriteTransaction() {
        var repository = new InMemoryBusinessUnits();
        var transaction = new CountingTransaction();
        var service = new BusinessUnitReferenceService(repository, transaction);
        var stored = unit(ID, "CLIENT-A", 1, "Synthetic Client A");
        service.execute(stored);
        int writesSoFar = transaction.opened;

        assertEquals(stored, service.execute(stored));
        assertEquals(writesSoFar, transaction.opened, "an exact repeat must not write again");
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

    /** Counts how often a write transaction is opened, so a rejection cannot hide inside one. */
    private static final class CountingTransaction
            implements io.stewardmesh.masterdata.application.port.out.ApplicationTransaction {

        private int opened;

        @Override
        public <T> T execute(java.util.function.Supplier<T> operation) {
            opened++;
            return operation.get();
        }
    }

}
