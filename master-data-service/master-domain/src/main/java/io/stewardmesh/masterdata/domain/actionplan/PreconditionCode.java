package io.stewardmesh.masterdata.domain.actionplan;

/**
 * Stable machine-readable reason a step cannot execute against current master data. Codes never
 * carry supplier values; the referenced identifiers stay in the plan step itself.
 */
public enum PreconditionCode {
    STEP_TARGET_DUPLICATED,
    SOURCE_RECORD_NOT_FOUND,
    PARTY_ALREADY_EXISTS,
    PARTY_NOT_FOUND,
    PARTY_VERSION_STALE,
    SITE_ALREADY_EXISTS,
    SITE_NOT_FOUND,
    SITE_VERSION_STALE,
    ADDRESS_NOT_FOUND,
    BUSINESS_UNIT_NOT_FOUND,
    BUSINESS_UNIT_ROLE_INVALID,
    ASSIGNMENT_ALREADY_EXISTS,
    ASSIGNMENT_OVERLAPS_EXISTING
}
