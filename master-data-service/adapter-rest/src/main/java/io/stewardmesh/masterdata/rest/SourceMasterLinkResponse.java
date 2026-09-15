package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.application.goldenrecord.SourceMasterLink;
import java.util.UUID;

public record SourceMasterLinkResponse(
        SourceIdentityResponse source,
        UUID associationId,
        UUID partyId,
        UUID addressId,
        UUID siteId) {

    static SourceMasterLinkResponse from(SourceMasterLink link) {
        return new SourceMasterLinkResponse(
                SourceIdentityResponse.from(link.source()), link.associationId(),
                link.partyId(), link.addressId(), link.siteId());
    }
}
