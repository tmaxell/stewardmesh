package io.stewardmesh.masterdata.persistence.jpa;

import org.springframework.data.jpa.repository.JpaRepository;

interface SpringDataIdempotencyRepository
        extends JpaRepository<IdempotencyEntity, IdempotencyEntityId> {}
