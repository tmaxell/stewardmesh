package io.stewardmesh.masterdata.domain.goldenrecord;

import io.stewardmesh.masterdata.domain.identity.MatchDecision;
import io.stewardmesh.masterdata.domain.identity.MatchEntityType;
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
        AssociationEvidence partyEvidence,
        Optional<AssociationEvidence> siteEvidence,
        Instant linkedAt,
        Optional<Instant> unlinkedAt) {

    public SourceAssociation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(sourceRecord, "sourceRecord must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        addressId = Objects.requireNonNull(addressId, "addressId must not be null");
        siteId = Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(partyEvidence, "partyEvidence must not be null");
        siteEvidence = Objects.requireNonNull(siteEvidence, "siteEvidence must not be null");
        Objects.requireNonNull(linkedAt, "linkedAt must not be null");
        unlinkedAt = Objects.requireNonNull(unlinkedAt, "unlinkedAt must not be null");

        partyEvidence.requireTarget(MatchEntityType.PARTY, partyId.value());
        if (siteId.isPresent()) {
            if (addressId.isEmpty() || siteEvidence.isEmpty()) {
                throw new IllegalArgumentException(
                        "a site association requires an address and site evidence");
            }
            siteEvidence.orElseThrow().requireTarget(
                    MatchEntityType.SITE, siteId.orElseThrow().value());
        } else if (siteEvidence.isPresent()) {
            throw new IllegalArgumentException("site evidence requires a site association");
        }
        unlinkedAt.ifPresent(endedAt -> {
            if (!endedAt.isAfter(linkedAt)) {
                throw new IllegalArgumentException("unlinkedAt must be after linkedAt");
            }
        });
    }

    public SourceAssociation(
            SourceAssociationId id,
            SourceRecordIdentity sourceRecord,
            SupplierPartyId partyId,
            Optional<SupplierAddressId> addressId,
            Optional<SupplierSiteId> siteId,
            MatchDecision partyDecision,
            Optional<MatchDecision> siteDecision,
            Instant linkedAt,
            Optional<Instant> unlinkedAt) {
        this(
                id,
                sourceRecord,
                partyId,
                addressId,
                siteId,
                AssociationEvidence.autoLinked(partyDecision),
                siteDecision.map(AssociationEvidence::autoLinked),
                linkedAt,
                unlinkedAt);
    }

    public boolean active() {
        return unlinkedAt.isEmpty();
    }

    public MatchDecision partyDecision() {
        return partyEvidence.matchDecision().orElseThrow(
                () -> new IllegalStateException("new-entity association has no party match decision"));
    }

    public Optional<MatchDecision> siteDecision() {
        return siteEvidence.flatMap(AssociationEvidence::matchDecision);
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
                partyEvidence,
                siteEvidence,
                linkedAt,
                Optional.of(endedAt));
    }

}
