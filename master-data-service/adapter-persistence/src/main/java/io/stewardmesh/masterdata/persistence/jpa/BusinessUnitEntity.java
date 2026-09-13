package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "business_unit")
class BusinessUnitEntity {

    @Id
    @Column(name = "business_unit_id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "display_name", nullable = false, length = 256)
    private String displayName;

    @Column(name = "roles", nullable = false, length = 32)
    private String roles;

    @Column(name = "reference_version", nullable = false)
    private long referenceVersion;

    @Column(name = "valid_from", nullable = false)
    private LocalDate validFrom;

    @Column(name = "valid_to")
    private LocalDate validTo;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected BusinessUnitEntity() {}

    BusinessUnitEntity(BusinessUnit unit) {
        id = unit.id().value();
        code = unit.code().value();
        apply(unit);
    }

    void advance(BusinessUnit unit) {
        if (!id.equals(unit.id().value()) || !code.equals(unit.code().value())) {
            throw new IllegalArgumentException("business unit identity and code are immutable");
        }
        if (unit.referenceVersion() <= referenceVersion) {
            throw new IllegalArgumentException("business unit reference version must advance");
        }
        apply(unit);
    }

    UUID id() {
        return id;
    }

    String code() {
        return code;
    }

    String displayName() {
        return displayName;
    }

    String roles() {
        return roles;
    }

    long referenceVersion() {
        return referenceVersion;
    }

    LocalDate validFrom() {
        return validFrom;
    }

    LocalDate validTo() {
        return validTo;
    }

    private void apply(BusinessUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        displayName = unit.displayName();
        roles = unit.roles().stream().map(Enum::name).sorted().collect(java.util.stream.Collectors.joining(","));
        referenceVersion = unit.referenceVersion();
        validFrom = unit.validFrom();
        validTo = unit.validTo().orElse(null);
    }
}
