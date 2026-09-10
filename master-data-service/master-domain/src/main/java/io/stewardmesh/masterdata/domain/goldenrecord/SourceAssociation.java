package io.stewardmesh.masterdata.domain.goldenrecord;

import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
import io.stewardmesh.masterdata.domain.identity.MatchOutcome;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Explainable, reversible association between an immutable source assertion and mastered targets.
 * Ending an association retains its identity and match evidence.
 */
public record SourceAssociation(
        SourceAssociationId id,
        SourceRecordIdentity sourceRecord,
        SupplierPartyId partyId,
        Optional<SupplierAddressId> addressId,
        Optional<SupplierSiteId> siteId,
        MatchDecision partyDecision,
        Optional<MatchDecision> siteDecision,
        Instant linkedAt,
        Optional<Instant> unlinkedAt) {

    public SourceAssociation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sourceRecord, "sourceRecord must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        addressId = Objects.requireNonNull(addressId, "addressId must not be null");
        siteId = Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(partyDecision, "partyDecision must not be null");
        siteDecision = Objects.requireNonNull(siteDecision, "siteDecision must not be null");
        Objects.requireNonNull(linkedAt, "linkedAt must not be null");
        unlinkedAt = Objects.requireNonNull(unlinkedAt, "unlinkedAt must not be null");

        requireAutoLink(partyDecision, MatchEntityType.PARTY, partyId.value());
        if (siteId.isPresent()) {
            if (addressId.isEmpty() || siteDecision.isEmpty()) {
                throw new IllegalArgumentException(
                        "a site association requires an address and site match evidence");
            }
            requireAutoLink(siteDecision.orElseThrow(), MatchEntityType.SITE, siteId.orElseThrow().value());
        } else if (siteDecision.isPresent()) {
            throw new IllegalArgumentException("site match evidence requires a site association");
        }
        unlinkedAt.ifPresent(endedAt -> {
            if (!endedAt.isAfter(linkedAt)) {
                throw new IllegalArgumentException("unlinkedAt must be after linkedAt");
            }
        });
    }

    public boolean active() {
        return unlinkedAt.isEmpty();
    }

    public SourceAssociation unlink(Instant endedAt) {
        Objects.requireNonNull(endedAt, "endedAt must not be null");
        if (!active()) {
            throw new IllegalStateException("source association is already unlinked");
        }
        return new SourceAssociation(
                id,
                sourceRecord,
                partyId,
                addressId,
                siteId,
                partyDecision,
                siteDecision,
                linkedAt,
                Optional.of(endedAt));
    }

    private static void requireAutoLink(
            MatchDecision decision, MatchEntityType entityType, java.util.UUID targetId) {
        if (decision.entityType() != entityType || !decision.candidateId().equals(targetId)) {
            throw new IllegalArgumentException("match evidence does not identify the associated target");
        }
        if (decision.outcome() != MatchOutcome.AUTO_LINK || decision.hardConflict()) {
            throw new IllegalArgumentException("only a conflict-free AUTO_LINK decision can be associated");
        }
    }
}
