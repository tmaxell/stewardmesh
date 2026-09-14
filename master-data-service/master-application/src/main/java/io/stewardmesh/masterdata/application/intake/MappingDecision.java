package io.stewardmesh.masterdata.application.intake;

/** Deterministic basis for a proposed source-to-canonical column mapping. */
public enum MappingDecision {
    EXACT_CANONICAL,
    KNOWN_ALIAS,
    TARGET_CONFLICT,
    UNMAPPED
}
