package io.stewardmesh.masterdata.domain.organization;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a supplier-site assignment across its versions. */
public record SiteAssignmentId(UUID value) {

    public SiteAssignmentId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
