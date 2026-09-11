package io.stewardmesh.masterdata.domain.identity;

import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import java.util.Objects;

/** Normalized mastered-party values needed by the scorer, independent of persistence. */
public record PartyMatchProfile(
        SupplierPartyId partyId, String inn, String ogrn, String legalName) {

    public PartyMatchProfile {
        Objects.requireNonNull(partyId, "partyId must not be null");
        inn = required(inn, "inn");
        ogrn = optional(ogrn);
        legalName = required(legalName, "legalName");
    }

    private static String required(String value, String name) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }

    private static String optional(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
