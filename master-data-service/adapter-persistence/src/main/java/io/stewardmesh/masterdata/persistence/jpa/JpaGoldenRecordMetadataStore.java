package io.stewardmesh.masterdata.persistence.jpa;

import io.stewardmesh.masterdata.domain.goldenrecord.GoldenEntityType;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenRecordVersion;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierAddress;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierParty;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierSite;
import io.stewardmesh.masterdata.domain.goldenrecord.SurvivorshipRulesetId;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** JPA-owned mutable aggregate metadata; immutable versions and attributes remain JDBC projections. */
public class JpaGoldenRecordMetadataStore {

    private final SpringDataGoldenRecordMetadataRepository repository;

    JpaGoldenRecordMetadataStore(SpringDataGoldenRecordMetadataRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    public void advance(SupplierParty party) {
        advance(
                GoldenEntityType.PARTY,
                party.id().value(),
                party.id().value(),
                null,
                party.version(),
                party.rulesetId(),
                party.projectedAt());
    }

    public void advance(SupplierAddress address) {
        advance(
                GoldenEntityType.ADDRESS,
                address.id().value(),
                address.partyId().value(),
                address.id().value(),
                address.version(),
                address.rulesetId(),
                address.projectedAt());
    }

    public void advance(SupplierSite site) {
        advance(
                GoldenEntityType.SITE,
                site.id().value(),
                site.partyId().value(),
                site.addressId().value(),
                site.version(),
                site.rulesetId(),
                site.projectedAt());
    }

    private void advance(
            GoldenEntityType entityType,
            UUID entityId,
            UUID partyId,
            UUID addressId,
            GoldenRecordVersion version,
            SurvivorshipRulesetId rulesetId,
            Instant projectedAt) {
        var id = new GoldenRecordMetadataId(entityType, entityId);
        var existing = repository.findById(id);
        var metadata = existing.orElseGet(() -> new GoldenRecordMetadataEntity(
                entityType, entityId, partyId, addressId, version, rulesetId, projectedAt));
        if (existing.isPresent()) {
            metadata.advance(partyId, addressId, version, rulesetId, projectedAt);
        }
        repository.saveAndFlush(metadata);
    }
}
