package io.stewardmesh.masterdata.domain.goldenrecord;

import java.util.Set;

/** Canonical supplier fields that can participate in the initial golden projection. */
public enum GoldenAttributeName {
    LEGAL_NAME("legal_name", GoldenEntityType.PARTY),
    INN("inn", GoldenEntityType.PARTY),
    OGRN("ogrn", GoldenEntityType.PARTY),
    COUNTRY_CODE("country_code", GoldenEntityType.ADDRESS),
    POSTAL_CODE("postal_code", GoldenEntityType.ADDRESS),
    REGION("region", GoldenEntityType.ADDRESS),
    CITY("city", GoldenEntityType.ADDRESS),
    ADDRESS_LINE("address_line", GoldenEntityType.ADDRESS),
    KPP("kpp", GoldenEntityType.SITE),
    SITE_CODE("site_code", GoldenEntityType.SITE),
    PROCUREMENT_BUSINESS_UNIT_CODE("procurement_bu_code", GoldenEntityType.SITE),
    SITE_PURPOSE("site_purpose", GoldenEntityType.SITE);

    private final String sourceField;
    private final GoldenEntityType entityType;

    GoldenAttributeName(String sourceField, GoldenEntityType entityType) {
        this.sourceField = sourceField;
        this.entityType = entityType;
    }

    public String sourceField() {
        return sourceField;
    }

    public GoldenEntityType entityType() {
        return entityType;
    }

    public static Set<GoldenAttributeName> forEntity(GoldenEntityType entityType) {
        var attributes = java.util.EnumSet.noneOf(GoldenAttributeName.class);
        for (var attribute : values()) {
            if (attribute.entityType == entityType) {
                attributes.add(attribute);
            }
        }
        return Set.copyOf(attributes);
    }
}
