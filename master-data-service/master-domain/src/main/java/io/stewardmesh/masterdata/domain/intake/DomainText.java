package io.stewardmesh.masterdata.domain.intake;

import java.util.Objects;

final class DomainText {

    private DomainText() {}

    static String requireNonBlank(String value, String name, int maxLength) {
        Objects.requireNonNull(value, name + " must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(name + " must not exceed " + maxLength + " characters");
        }
        return value;
    }
}
