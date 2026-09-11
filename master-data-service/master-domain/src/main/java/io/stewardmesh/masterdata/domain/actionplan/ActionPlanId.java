package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Objects;
import java.util.UUID;

/** Stable identity of a governed proposal across its immutable versions. */
public record ActionPlanId(UUID value) {

    public ActionPlanId {
        Objects.requireNonNull(value, "value must not be null");
    }
}
