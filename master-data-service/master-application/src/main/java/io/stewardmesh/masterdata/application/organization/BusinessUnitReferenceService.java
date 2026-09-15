package io.stewardmesh.masterdata.application.organization;

import io.stewardmesh.masterdata.application.port.in.SynchronizeBusinessUnit;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import java.util.Objects;
import java.util.Optional;

/** Applies monotonic internal-reference updates without mastering the organization hierarchy. */
public final class BusinessUnitReferenceService implements SynchronizeBusinessUnit {

    private final BusinessUnitRepository businessUnits;
    private final ApplicationTransaction transaction;

    public BusinessUnitReferenceService(
            BusinessUnitRepository businessUnits, ApplicationTransaction transaction) {
        this.businessUnits = Objects.requireNonNull(businessUnits, "businessUnits must not be null");
        this.transaction = Objects.requireNonNull(transaction, "transaction must not be null");
    }

    @Override
    public BusinessUnit execute(BusinessUnit incoming) {
        Objects.requireNonNull(incoming, "incoming must not be null");
        // Rejections are decided before the write starts, deliberately outside the transaction
        // template. A nested template that fails marks the caller's transaction rollback-only, and
        // an inbox consumer would then be unable to commit the quarantine record describing the
        // rejection: the event would be neither applied nor recorded, only redelivered forever.
        Optional<BusinessUnit> unchanged = reject(incoming);
        return unchanged.orElseGet(() -> transaction.execute(() -> businessUnits.save(incoming)));
    }

    /** Returns the stored unit when the event is an exact repeat, or throws when it conflicts. */
    private Optional<BusinessUnit> reject(BusinessUnit incoming) {
        businessUnits.findByCode(incoming.code()).ifPresent(byCode -> {
            if (!byCode.id().equals(incoming.id())) {
                throw new BusinessUnitConflictException("business unit code belongs to another identity");
            }
        });
        Optional<BusinessUnit> stored = businessUnits.findById(incoming.id());
        if (stored.isEmpty()) {
            return Optional.empty();
        }
        BusinessUnit current = stored.orElseThrow();
        if (!current.code().equals(incoming.code())) {
            throw new BusinessUnitConflictException("business unit code is immutable");
        }
        if (incoming.referenceVersion() < current.referenceVersion()) {
            throw new BusinessUnitConflictException("business unit reference version is stale");
        }
        if (incoming.referenceVersion() == current.referenceVersion()) {
            if (!incoming.equals(current)) {
                throw new BusinessUnitConflictException("business unit version has conflicting content");
            }
            return Optional.of(current);
        }
        return Optional.empty();
    }
}
