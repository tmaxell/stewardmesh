package io.stewardmesh.masterdata.application.goldenrecord;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.util.Objects;
import java.util.UUID;

/** Active projection targets for one immutable source assertion. */
public record SourceMasterLink(
        SourceRecordIdentity source,
        UUID associationId,
        UUID partyId,
        UUID addressId,
        UUID siteId) {

    public SourceMasterLink {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(associationId, "associationId must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        if ((addressId == null) != (siteId == null)) {
            throw new IllegalArgumentException("site and address targets must be present together");
        }
    }
}
