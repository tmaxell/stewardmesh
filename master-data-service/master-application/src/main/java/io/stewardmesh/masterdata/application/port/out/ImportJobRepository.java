package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.intake.ImportJob;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import java.util.Optional;

/** Persists import aggregate versions without exposing storage technology. */
public interface ImportJobRepository {

    Optional<ImportJob> findById(ImportJobId importJobId);

    void save(ImportJob importJob);
}
