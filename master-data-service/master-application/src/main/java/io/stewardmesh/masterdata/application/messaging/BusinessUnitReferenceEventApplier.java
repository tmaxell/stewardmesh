package io.stewardmesh.masterdata.application.messaging;

import io.stewardmesh.masterdata.application.organization.BusinessUnitNotFoundException;
import io.stewardmesh.masterdata.application.port.in.GetBusinessUnit;
import io.stewardmesh.masterdata.application.port.in.SynchronizeBusinessUnit;
import io.stewardmesh.masterdata.application.port.out.ReferenceDataEventApplier;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitCode;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitRole;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Maps the first governed inbound reference contract without leaking transport details inward. */
public final class BusinessUnitReferenceEventApplier implements ReferenceDataEventApplier {
    public static final String EVENT_TYPE = "BusinessUnitReferenceChanged";
    private final GetBusinessUnit getBusinessUnit;
    private final SynchronizeBusinessUnit synchronizeBusinessUnit;

    public BusinessUnitReferenceEventApplier(
            GetBusinessUnit getBusinessUnit, SynchronizeBusinessUnit synchronizeBusinessUnit) {
        this.getBusinessUnit = Objects.requireNonNull(getBusinessUnit, "getBusinessUnit must not be null");
        this.synchronizeBusinessUnit = Objects.requireNonNull(
                synchronizeBusinessUnit, "synchronizeBusinessUnit must not be null");
    }

    @Override
    public void apply(CanonicalEventEnvelope envelope) {
        if (!EVENT_TYPE.equals(envelope.eventType()) || !"BUSINESS_UNIT".equals(envelope.subjectType())) {
            throw new ReferenceEventRejectedException("UNSUPPORTED_EVENT_TYPE", "unsupported reference event");
        }
        try {
            BusinessUnitId id = new BusinessUnitId(UUID.fromString(envelope.subjectId()));
            rejectNonMonotonicVersion(id, envelope.entityVersion());
            var payload = envelope.payload();
            var roles = EnumSet.noneOf(BusinessUnitRole.class);
            Arrays.stream(required(payload.get("roles"), "roles").split(","))
                    .map(String::strip).map(BusinessUnitRole::valueOf).forEach(roles::add);
            synchronizeBusinessUnit.execute(new BusinessUnit(
                    id,
                    new BusinessUnitCode(required(payload.get("code"), "code")),
                    required(payload.get("displayName"), "displayName"),
                    roles,
                    envelope.entityVersion(),
                    LocalDate.parse(required(payload.get("validFrom"), "validFrom")),
                    Optional.ofNullable(payload.get("validTo")).map(LocalDate::parse)));
        } catch (ReferenceEventRejectedException rejected) {
            throw rejected;
        } catch (RuntimeException invalid) {
            throw new ReferenceEventRejectedException("INVALID_EVENT_PAYLOAD", "invalid business unit payload");
        }
    }

    private void rejectNonMonotonicVersion(BusinessUnitId id, long incomingVersion) {
        try {
            long current = getBusinessUnit.execute(id).referenceVersion();
            if (incomingVersion < current) {
                throw new ReferenceEventRejectedException("STALE_VERSION", "stale business unit version");
            }
            if (incomingVersion > current + 1) {
                throw new ReferenceEventRejectedException("VERSION_GAP", "business unit version gap");
            }
        } catch (BusinessUnitNotFoundException missing) {
            if (incomingVersion != 1) {
                throw new ReferenceEventRejectedException("VERSION_GAP", "initial business unit version must be one");
            }
        }
    }

    private static String required(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value;
    }
}
