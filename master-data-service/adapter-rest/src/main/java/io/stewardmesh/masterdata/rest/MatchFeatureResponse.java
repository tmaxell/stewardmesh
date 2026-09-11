package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.domain.identity.MatchFeature;

public record MatchFeatureResponse(String code, String signal, int contributionBasisPoints) {

    static MatchFeatureResponse from(MatchFeature feature) {
        return new MatchFeatureResponse(
                feature.code().name(), feature.signal().name(), feature.contributionBasisPoints());
    }
}
