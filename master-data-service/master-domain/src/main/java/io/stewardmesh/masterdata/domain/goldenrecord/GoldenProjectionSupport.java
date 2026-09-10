package io.stewardmesh.masterdata.domain.goldenrecord;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class GoldenProjectionSupport {

    private GoldenProjectionSupport() {}

    static Map<GoldenAttributeName, GoldenAttribute> attributes(
            Map<GoldenAttributeName, GoldenAttribute> attributes,
            GoldenEntityType entityType,
            Set<GoldenAttributeName> required) {
        var copy = new EnumMap<GoldenAttributeName, GoldenAttribute>(GoldenAttributeName.class);
        Objects.requireNonNull(attributes, "attributes must not be null")
                .forEach((name, attribute) -> {
                    Objects.requireNonNull(name, "attribute name must not be null");
                    Objects.requireNonNull(attribute, "attribute must not be null");
                    if (name != attribute.name() || name.entityType() != entityType) {
                        throw new IllegalArgumentException("golden attribute belongs to a different entity type");
                    }
                    copy.put(name, attribute);
                });
        if (!copy.keySet().containsAll(required)) {
            throw new IllegalArgumentException("required golden attributes are missing");
        }
        return Collections.unmodifiableMap(copy);
    }
}
