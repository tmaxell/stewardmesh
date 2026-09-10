package io.stewardmesh.masterdata.domain.goldenrecord;

import io.stewardmesh.masterdata.domain.model.SupplierAddressId;
import io.stewardmesh.masterdata.domain.model.SupplierPartyId;
import io.stewardmesh.masterdata.domain.model.SupplierSiteId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;

/** Deterministic initial survivorship policy for golden supplier projections. */
public final class GoldenRecordProjector {

    public static final SurvivorshipRulesetId RULESET_ID =
            new SurvivorshipRulesetId("supplier-survivorship-v1");
    public static final int MAX_ASSERTIONS = 1_000;

    private static final Comparator<GoldenSourceAssertion> WINNER_ORDER =
            Comparator.<GoldenSourceAssertion>comparingInt(assertion -> assertion.priority().value())
                    .reversed()
                    .thenComparing(
                            Comparator.comparing(GoldenSourceAssertion::ingestedAt).reversed())
                    .thenComparing(
                            Comparator.comparingInt(GoldenSourceAssertion::completeness).reversed())
                    .thenComparing(assertion -> assertion.sourceRecord().originSystem().value())
                    .thenComparing(assertion -> assertion.sourceRecord().sourceRecordId())
                    .thenComparingLong(assertion -> assertion.sourceRecord().sourceVersion())
                    .thenComparing(assertion -> assertion.association().id().value());

    public SupplierParty projectParty(
            SupplierPartyId partyId,
            GoldenRecordVersion version,
            Instant projectedAt,
            List<GoldenSourceAssertion> assertions) {
        Objects.requireNonNull(partyId, "partyId must not be null");
        var eligible = eligible(assertions, assertion -> assertion.association().partyId().equals(partyId));
        return new SupplierParty(
                partyId,
                version,
                RULESET_ID,
                projectedAt,
                selectAttributes(GoldenEntityType.PARTY, eligible, projectedAt),
                associationIds(eligible));
    }

    public SupplierAddress projectAddress(
            SupplierAddressId addressId,
            SupplierPartyId partyId,
            GoldenRecordVersion version,
            Instant projectedAt,
            List<GoldenSourceAssertion> assertions) {
        Objects.requireNonNull(addressId, "addressId must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        var eligible = eligible(
                assertions,
                assertion -> assertion.association().partyId().equals(partyId)
                        && assertion.association().addressId().filter(addressId::equals).isPresent());
        return new SupplierAddress(
                addressId,
                partyId,
                version,
                RULESET_ID,
                projectedAt,
                selectAttributes(GoldenEntityType.ADDRESS, eligible, projectedAt),
                associationIds(eligible));
    }

    public SupplierSite projectSite(
            SupplierSiteId siteId,
            SupplierPartyId partyId,
            SupplierAddressId addressId,
            GoldenRecordVersion version,
            Instant projectedAt,
            List<GoldenSourceAssertion> assertions) {
        Objects.requireNonNull(siteId, "siteId must not be null");
        Objects.requireNonNull(partyId, "partyId must not be null");
        Objects.requireNonNull(addressId, "addressId must not be null");
        var eligible = eligible(
                assertions,
                assertion -> assertion.association().partyId().equals(partyId)
                        && assertion.association().addressId().filter(addressId::equals).isPresent()
                        && assertion.association().siteId().filter(siteId::equals).isPresent());
        return new SupplierSite(
                siteId,
                partyId,
                addressId,
                version,
                RULESET_ID,
                projectedAt,
                selectAttributes(GoldenEntityType.SITE, eligible, projectedAt),
                associationIds(eligible));
    }

    private static List<GoldenSourceAssertion> eligible(
            List<GoldenSourceAssertion> assertions, Predicate<GoldenSourceAssertion> target) {
        var input = List.copyOf(Objects.requireNonNull(assertions, "assertions must not be null"));
        if (input.isEmpty() || input.size() > MAX_ASSERTIONS) {
            throw new IllegalArgumentException("assertion count must be between 1 and 1000");
        }
        var eligible = input.stream()
                .filter(assertion -> assertion.association().active())
                .filter(target)
                .sorted(WINNER_ORDER)
                .toList();
        if (eligible.isEmpty()) {
            throw new IllegalArgumentException("no active source assertion identifies the projection target");
        }
        return eligible;
    }

    private static java.util.Map<GoldenAttributeName, GoldenAttribute> selectAttributes(
            GoldenEntityType entityType,
            List<GoldenSourceAssertion> assertions,
            Instant projectedAt) {
        Objects.requireNonNull(projectedAt, "projectedAt must not be null");
        var attributes = new EnumMap<GoldenAttributeName, GoldenAttribute>(GoldenAttributeName.class);
        for (var name : GoldenAttributeName.values()) {
            if (name.entityType() != entityType) {
                continue;
            }
            var candidates = assertions.stream()
                    .filter(assertion -> assertion.canonicalValues().containsKey(name))
                    .sorted(WINNER_ORDER)
                    .toList();
            if (!candidates.isEmpty()) {
                var winner = candidates.getFirst();
                var provenance = new GoldenAttributeProvenance(
                        winner.sourceRecord(),
                        winner.association().id(),
                        explainRule(winner, candidates, assertions),
                        RULESET_ID,
                        projectedAt);
                attributes.put(name, new GoldenAttribute(
                        name, winner.canonicalValues().get(name), provenance));
            }
        }
        return attributes;
    }

    private static SurvivorshipRule explainRule(
            GoldenSourceAssertion winner,
            List<GoldenSourceAssertion> candidates,
            List<GoldenSourceAssertion> allAssertions) {
        int highestPriority = allAssertions.stream()
                .mapToInt(assertion -> assertion.priority().value())
                .max()
                .orElseThrow();
        if (winner.priority().value() < highestPriority) {
            return SurvivorshipRule.SOURCE_PRIORITY_FALLBACK;
        }
        if (candidates.size() == 1) {
            return SurvivorshipRule.TRUSTED_SOURCE;
        }
        var runnerUp = candidates.get(1);
        if (winner.priority().value() != runnerUp.priority().value()) {
            return SurvivorshipRule.TRUSTED_SOURCE;
        }
        if (!winner.ingestedAt().equals(runnerUp.ingestedAt())) {
            return SurvivorshipRule.MOST_RECENT_VERIFIED;
        }
        if (winner.completeness() != runnerUp.completeness()) {
            return SurvivorshipRule.MOST_COMPLETE;
        }
        return SurvivorshipRule.DETERMINISTIC_TIE_BREAK;
    }

    private static List<SourceAssociationId> associationIds(List<GoldenSourceAssertion> assertions) {
        var ids = new ArrayList<SourceAssociationId>(assertions.size());
        assertions.forEach(assertion -> ids.add(assertion.association().id()));
        return List.copyOf(ids);
    }
}
