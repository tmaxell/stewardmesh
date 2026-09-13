package io.stewardmesh.masterdata.domain.organization;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Effective-dated authorization for a client business unit to use one supplier site. */
public record SiteAssignment(
        SiteAssignmentId id,
        SupplierSiteId siteId,
        BusinessUnitId clientBusinessUnitId,
        Set<SitePurpose> purposes,
        LocalDate validFrom,
        Optional<LocalDate> validTo,
        long version) {

    public SiteAssignment {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(clientBusinessUnitId, "clientBusinessUnitId must not be null");
        Objects.requireNonNull(purposes, "purposes must not be null");
        if (purposes.isEmpty() || purposes.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("site assignment requires non-null purposes");
        }
        purposes = Set.copyOf(EnumSet.copyOf(purposes));
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        validTo = Objects.requireNonNull(validTo, "validTo must not be null");
        validTo.ifPresent(end -> {
            if (end.isBefore(validFrom)) {
                throw new IllegalArgumentException("site assignment validity interval is invalid");
            }
        });
        if (version <= 0) {
            throw new IllegalArgumentException("site assignment version must be positive");
        }
    }

    public boolean isEffectiveOn(LocalDate date) {
        Objects.requireNonNull(date, "date must not be null");
        return !date.isBefore(validFrom) && validTo.map(end -> !date.isAfter(end)).orElse(true);
    }

    public boolean conflictsWith(SiteAssignment other) {
        Objects.requireNonNull(other, "other must not be null");
        if (!siteId.equals(other.siteId)
                || !clientBusinessUnitId.equals(other.clientBusinessUnitId)
                || purposes.stream().noneMatch(other.purposes::contains)) {
            return false;
        }
        return startsBeforeOrOnEnd(validFrom, other.validTo)
                && startsBeforeOrOnEnd(other.validFrom, validTo);
    }

    private static boolean startsBeforeOrOnEnd(LocalDate start, Optional<LocalDate> end) {
        return end.map(value -> !start.isAfter(value)).orElse(true);
    }
}
