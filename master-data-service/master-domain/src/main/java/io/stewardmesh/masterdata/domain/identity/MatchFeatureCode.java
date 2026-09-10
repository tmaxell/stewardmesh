package io.stewardmesh.masterdata.domain.identity;

/** Stable, non-sensitive feature identifiers used in match explanations. */
public enum MatchFeatureCode {
    INN_EXACT,
    OGRN_EXACT,
    KPP_EXACT,
    LEGAL_NAME_SIMILARITY,
    ADDRESS_SIMILARITY,
    SITE_CODE_EXACT,
    AUTHORITATIVE_IDENTIFIER_CONFLICT
}
