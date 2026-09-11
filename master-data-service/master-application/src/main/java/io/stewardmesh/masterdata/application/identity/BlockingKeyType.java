package io.stewardmesh.masterdata.application.identity;

/** Stable evidence describing why a record entered a bounded candidate set. */
public enum BlockingKeyType {
    PARTY_INN_EXACT,
    PARTY_OGRN_EXACT,
    SITE_INN_KPP_EXACT,
    SITE_CODE_EXACT,
    SITE_ADDRESS_COARSE
}
