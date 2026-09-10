package io.stewardmesh.masterdata.application.goldenrecord;

import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociation;
import io.stewardmesh.masterdata.domain.goldenrecord.SourceAssociationId;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierAddress;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierParty;
import io.stewardmesh.masterdata.domain.goldenrecord.SupplierSite;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Atomic persistence unit for a party projection and any recalculated addresses and sites. */
public record GoldenRecordProjection(
        SupplierParty party,
        List<SupplierAddress> addresses,
        List<SupplierSite> sites,
        List<SourceAssociation> sourceAssociations) {

    public GoldenRecordProjection {
        Objects.requireNonNull(party, "party must not be null");
        addresses = immutableUnique(addresses, "addresses", address -> address.id().value());
        sites = immutableUnique(sites, "sites", site -> site.id().value());
        sourceAssociations = immutableUnique(
                sourceAssociations, "sourceAssociations", association -> association.id().value());

        if (addresses.stream().anyMatch(address -> !address.partyId().equals(party.id()))
                || sites.stream().anyMatch(site -> !site.partyId().equals(party.id()))
                || sourceAssociations.stream()
                        .anyMatch(association -> !association.partyId().equals(party.id()))) {
            throw new IllegalArgumentException("golden projection mixes supplier parties");
        }
        Set<SourceAssociationId> suppliedAssociations = sourceAssociations.stream()
                .map(SourceAssociation::id)
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        var referencedAssociations = new HashSet<>(party.sourceAssociations());
        addresses.forEach(address -> referencedAssociations.addAll(address.sourceAssociations()));
        sites.forEach(site -> referencedAssociations.addAll(site.sourceAssociations()));
        if (!suppliedAssociations.containsAll(referencedAssociations)) {
            throw new IllegalArgumentException("every projected association must be supplied for persistence");
        }
    }

    private static <T> List<T> immutableUnique(
            List<T> values, String name, java.util.function.Function<T, UUID> identity) {
        var copy = List.copyOf(Objects.requireNonNull(values, name + " must not be null"));
        var identifiers = new HashSet<UUID>();
        if (copy.stream().anyMatch(value -> value == null || !identifiers.add(identity.apply(value)))) {
            throw new IllegalArgumentException(name + " must contain unique non-null values");
        }
        return copy;
    }
}
