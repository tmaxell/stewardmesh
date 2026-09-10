package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRulesetId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "golden_record_metadata")
class GoldenRecordMetadataEntity {

    @EmbeddedId
    private GoldenRecordMetadataId id;

    @Column(name = "party_id", nullable = false, updatable = false)
    private UUID partyId;

    @Column(name = "address_id", updatable = false)
    private UUID addressId;

    @Column(name = "projection_version", nullable = false)
    private long projectionVersion;

    @Column(name = "survivorship_ruleset", nullable = false, length = 64)
    private String survivorshipRuleset;

    @Column(name = "projected_at", nullable = false)
    private Instant projectedAt;

    @Version
    @Column(name = "lock_version", nullable = false)
    private long lockVersion;

    protected GoldenRecordMetadataEntity() {}

    GoldenRecordMetadataEntity(
            GoldenEntityType entityType,
            UUID entityId,
            UUID partyId,
            UUID addressId,
            GoldenRecordVersion projectionVersion,
            SurvivorshipRulesetId rulesetId,
            Instant projectedAt) {
        id = new GoldenRecordMetadataId(entityType, entityId);
        this.partyId = Objects.requireNonNull(partyId, "partyId must not be null");
        this.addressId = addressId;
        this.projectionVersion = projectionVersion.value();
        survivorshipRuleset = rulesetId.value();
        this.projectedAt = Objects.requireNonNull(projectedAt, "projectedAt must not be null");
        if (projectionVersion.value() != 1) {
            throw new IllegalArgumentException("a new golden record must start at version 1");
        }
    }

    void advance(
            UUID partyId,
            UUID addressId,
            GoldenRecordVersion nextVersion,
            SurvivorshipRulesetId rulesetId,
            Instant nextProjectedAt) {
        if (!this.partyId.equals(partyId) || !Objects.equals(this.addressId, addressId)) {
            throw new IllegalArgumentException("golden record ownership cannot change");
        }
        if (nextVersion.value() != projectionVersion + 1) {
            throw new IllegalArgumentException("golden record versions must advance by one");
        }
        Objects.requireNonNull(nextProjectedAt, "nextProjectedAt must not be null");
        if (!nextProjectedAt.isAfter(projectedAt)) {
            throw new IllegalArgumentException("golden record decision time must advance");
        }
        projectionVersion = nextVersion.value();
        survivorshipRuleset = rulesetId.value();
        projectedAt = nextProjectedAt;
    }
}
