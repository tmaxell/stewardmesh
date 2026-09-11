package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;

public record SourceIdentityResponse(
        String originSystem, String sourceRecordId, long sourceVersion) {

    static SourceIdentityResponse from(SourceRecordIdentity identity) {
        return new SourceIdentityResponse(
                identity.originSystem().value(), identity.sourceRecordId(), identity.sourceVersion());
    }
}
