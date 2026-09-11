package io.stewardmesh.masterdata.domain.organization;

import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/** Versioned internal reference context; StewardMesh does not master its hierarchy. */
public record BusinessUnit(
        BusinessUnitId id,
        BusinessUnitCode code,
        String displayName,
        Set<BusinessUnitRole> roles,
        long referenceVersion,
        LocalDate validFrom,
        Optional<LocalDate> validTo) {

    private static final int MAX_DISPLAY_NAME_LENGTH = 256;

    public BusinessUnit {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(code, "code must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        if (displayName.isBlank() || displayName.length() > MAX_DISPLAY_NAME_LENGTH) {
            throw new IllegalArgumentException("display name must be between 1 and 256 characters");
        }
        Objects.requireNonNull(roles, "roles must not be null");
        if (roles.isEmpty() || roles.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("business unit requires non-null roles");
        }
        roles = Set.copyOf(EnumSet.copyOf(roles));
        if (referenceVersion <= 0) {
            throw new IllegalArgumentException("reference version must be positive");
        }
        Objects.requireNonNull(validFrom, "validFrom must not be null");
        validTo = Objects.requireNonNull(validTo, "validTo must not be null");
        validTo.ifPresent(end -> {
            if (end.isBefore(validFrom)) {
                throw new IllegalArgumentException("business unit validity interval is invalid");
            }
        });
    }

    public boolean supports(BusinessUnitRole role, LocalDate effectiveOn) {
        Objects.requireNonNull(role, "role must not be null");
        Objects.requireNonNull(effectiveOn, "effectiveOn must not be null");
        return roles.contains(role)
                && !effectiveOn.isBefore(validFrom)
                && validTo.map(end -> !effectiveOn.isAfter(end)).orElse(true);
    }

    public boolean supportsThroughout(
            BusinessUnitRole role, LocalDate intervalStart, Optional<LocalDate> intervalEnd) {
        Objects.requireNonNull(intervalEnd, "intervalEnd must not be null");
        return supports(role, intervalStart)
                && intervalEnd.map(end -> supports(role, end)).orElseGet(() -> validTo.isEmpty());
    }
}
