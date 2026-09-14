package io.stewardmesh.masterdata.application.port.in;

import io.stewardmesh.masterdata.application.intake.IntakeMappingSuggestion;
import io.stewardmesh.masterdata.domain.intake.ImportJobId;

/** Suggests a deterministic, value-free source-to-canonical column mapping. */
@FunctionalInterface
public interface SuggestIntakeMapping extends UseCase<ImportJobId, IntakeMappingSuggestion> {}
