package io.stewardmesh.masterdata.domain.goldenrecord;

/** Stable explanation of the material rule that selected a golden attribute. */
public enum SurvivorshipRule {
    TRUSTED_SOURCE,
    MOST_RECENT_VERIFIED,
    MOST_COMPLETE,
    SOURCE_PRIORITY_FALLBACK,
    DETERMINISTIC_TIE_BREAK
}
