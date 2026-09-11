package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.application.organization.OrganizationWriteException;
import io.stewardmesh.masterdata.application.port.out.BusinessUnitRepository;
import io.stewardmesh.masterdata.domain.model.BusinessUnitId;
import io.stewardmesh.masterdata.domain.organization.BusinessUnit;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitCode;
import io.stewardmesh.masterdata.domain.organization.BusinessUnitRole;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Optional;
import org.springframework.dao.DataAccessException;

public final class JpaBusinessUnitRepository implements BusinessUnitRepository {

    private final SpringDataBusinessUnitRepository repository;

    JpaBusinessUnitRepository(SpringDataBusinessUnitRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public Optional<BusinessUnit> findById(BusinessUnitId id) {
        Objects.requireNonNull(id, "id must not be null");
        return repository.findById(id.value()).map(JpaBusinessUnitRepository::toDomain);
    }

    @Override
    public Optional<BusinessUnit> findByCode(BusinessUnitCode code) {
        Objects.requireNonNull(code, "code must not be null");
        return repository.findByCode(code.value()).map(JpaBusinessUnitRepository::toDomain);
    }

    @Override
    public BusinessUnit save(BusinessUnit unit) {
        Objects.requireNonNull(unit, "unit must not be null");
        try {
            var current = repository.findById(unit.id().value());
            var entity = current.orElseGet(() -> new BusinessUnitEntity(unit));
            if (current.isPresent()) {
                entity.advance(unit);
            }
            repository.saveAndFlush(entity);
            return unit;
        } catch (DataAccessException | IllegalArgumentException exception) {
            throw new OrganizationWriteException("business unit could not be persisted", exception);
        }
    }

    private static BusinessUnit toDomain(BusinessUnitEntity entity) {
        var roles = EnumSet.noneOf(BusinessUnitRole.class);
        Arrays.stream(entity.roles().split(","))
                .map(BusinessUnitRole::valueOf)
                .forEach(roles::add);
        return new BusinessUnit(
                new BusinessUnitId(entity.id()),
                new BusinessUnitCode(entity.code()),
                entity.displayName(),
                roles,
                entity.referenceVersion(),
                entity.validFrom(),
                Optional.ofNullable(entity.validTo()));
    }
}
