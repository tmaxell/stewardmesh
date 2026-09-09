package io.stewardmesh.masterdata.persistence.jpa;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataImportJobRepository extends JpaRepository<ImportJobEntity, UUID> {}
