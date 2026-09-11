package io.stewardmesh.masterdata.domain.actionplan;

/** Provenance category for an exact, versioned fact used by a proposed step. */
public enum EvidenceType {
    SOURCE_RECORD,
    MATCH_EVALUATION,
    GOLDEN_RECORD,
    BUSINESS_UNIT_REFERENCE
}
