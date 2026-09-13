package io.stewardmesh.masterdata.persistence.jpa;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataBusinessUnitRepository extends JpaRepository<BusinessUnitEntity, UUID> {

    Optional<BusinessUnitEntity> findByCode(String code);
}
