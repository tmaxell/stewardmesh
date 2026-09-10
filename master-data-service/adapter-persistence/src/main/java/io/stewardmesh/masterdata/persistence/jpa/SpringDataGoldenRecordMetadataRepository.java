package io.stewardmesh.masterdata.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataGoldenRecordMetadataRepository
        extends JpaRepository<GoldenRecordMetadataEntity, GoldenRecordMetadataId> {}
