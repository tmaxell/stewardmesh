package io.stewardmesh.masterdata.application.organization;

import io.stewardmesh.masterdata.application.port.in.SynchronizeBusinessUnit;
import io.stewardmesh.masterdata.application.port.out.ApplicationTransaction;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import java.util.Objects;

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
        return transaction.execute(() -> synchronize(incoming));
    }

    private BusinessUnit synchronize(BusinessUnit incoming) {
        businessUnits.findByCode(incoming.code()).ifPresent(byCode -> {
            if (!byCode.id().equals(incoming.id())) {
                throw new BusinessUnitConflictException("business unit code belongs to another identity");
            }
        });
        return businessUnits.findById(incoming.id())
                .map(current -> update(current, incoming))
                .orElseGet(() -> businessUnits.save(incoming));
    }

    private BusinessUnit update(BusinessUnit current, BusinessUnit incoming) {
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
            return current;
        }
        return businessUnits.save(incoming);
    }
}
