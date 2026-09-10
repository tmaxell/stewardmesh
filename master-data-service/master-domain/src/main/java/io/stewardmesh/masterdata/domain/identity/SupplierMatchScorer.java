package io.stewardmesh.masterdata.domain.identity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/** Versioned rules-first supplier scorer with independently explainable contributions. */
public final class SupplierMatchScorer {

    public static final MatchRuleset RULESET = new MatchRuleset(
            new MatchRulesetId("supplier-identity-v1"),
            8_000,
            5_000,
            Set.of(
                    MatchFeatureCode.INN_EXACT,
                    MatchFeatureCode.OGRN_EXACT,
                    MatchFeatureCode.KPP_EXACT));

    private static final int PARTY_INN_WEIGHT = 4_000;
    private static final int PARTY_OGRN_WEIGHT = 2_000;
    private static final int LEGAL_NAME_WEIGHT = 4_000;
    private static final int SITE_INN_WEIGHT = 2_500;
    private static final int SITE_KPP_WEIGHT = 2_500;
    private static final int SITE_CODE_WEIGHT = 1_000;
    private static final int ADDRESS_WEIGHT = 4_000;

    public PartyCandidate scoreParty(SupplierMatchInput source, PartyMatchProfile candidate) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        List<MatchFeature> features = List.of(
                identifier(MatchFeatureCode.INN_EXACT, source.inn(), candidate.inn(), PARTY_INN_WEIGHT),
                identifier(MatchFeatureCode.OGRN_EXACT, source.ogrn(), candidate.ogrn(), PARTY_OGRN_WEIGHT),
                similarity(
                        MatchFeatureCode.LEGAL_NAME_SIMILARITY,
                        source.legalName(),
                        candidate.legalName(),
                        LEGAL_NAME_WEIGHT));
        return new PartyCandidate(candidate.partyId(), score(features), features);
    }

    public SiteCandidate scoreSite(SupplierMatchInput source, SiteMatchProfile candidate) {
        Objects.requireNonNull(source, "source must not be null");
        Objects.requireNonNull(candidate, "candidate must not be null");
        List<MatchFeature> features = new ArrayList<>();
        features.add(identifier(
                MatchFeatureCode.INN_EXACT, source.inn(), candidate.inn(), SITE_INN_WEIGHT));
        features.add(identifier(
                MatchFeatureCode.KPP_EXACT, source.kpp(), candidate.kpp(), SITE_KPP_WEIGHT));
        features.add(identifier(
                MatchFeatureCode.SITE_CODE_EXACT,
                source.siteCode(),
                candidate.siteCode(),
                SITE_CODE_WEIGHT));
        features.add(similarity(
                MatchFeatureCode.ADDRESS_SIMILARITY,
                address(source.countryCode(), source.city(), source.addressLine()),
                address(candidate.countryCode(), candidate.city(), candidate.addressLine()),
                ADDRESS_WEIGHT));
        return new SiteCandidate(candidate.siteId(), candidate.partyId(), score(features), features);
    }

    public MatchDecision decide(MatchCandidate candidate) {
        return RULESET.decide(candidate);
    }

    private static MatchFeature identifier(
            MatchFeatureCode code, String source, String candidate, int weight) {
        if (source == null || candidate == null) {
            return new MatchFeature(code, MatchSignal.MISSING, 0);
        }
        if (source.equals(candidate)) {
            return new MatchFeature(code, MatchSignal.MATCH, weight);
        }
        return new MatchFeature(code, MatchSignal.CONFLICT, 0);
    }

    private static MatchFeature similarity(
            MatchFeatureCode code, String source, String candidate, int weight) {
        int basisPoints = tokenJaccard(source, candidate);
        MatchSignal signal = basisPoints == 0 ? MatchSignal.MISMATCH : MatchSignal.MATCH;
        return new MatchFeature(code, signal, Math.floorDiv(basisPoints * weight, 10_000));
    }

    private static int tokenJaccard(String first, String second) {
        Set<String> firstTokens = tokens(first);
        Set<String> secondTokens = tokens(second);
        var union = new HashSet<>(firstTokens);
        union.addAll(secondTokens);
        var intersection = new HashSet<>(firstTokens);
        intersection.retainAll(secondTokens);
        return Math.floorDiv(intersection.size() * 10_000, union.size());
    }

    private static Set<String> tokens(String value) {
        Set<String> tokens = Arrays.stream(value.split("[^\\p{L}\\p{N}]+"))
                .filter(token -> !token.isBlank())
                .collect(Collectors.toUnmodifiableSet());
        if (tokens.isEmpty()) {
            throw new IllegalArgumentException("similarity value must contain letters or digits");
        }
        return tokens;
    }

    private static int score(List<MatchFeature> features) {
        return features.stream().mapToInt(MatchFeature::contributionBasisPoints).sum();
    }

    private static String address(String countryCode, String city, String addressLine) {
        return countryCode + " " + city + " " + addressLine;
    }
}
