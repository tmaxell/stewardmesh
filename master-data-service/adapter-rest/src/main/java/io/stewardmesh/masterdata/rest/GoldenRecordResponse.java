package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.application.goldenrecord.GoldenRecordView;
import io.stewardmesh.masterdata.domain.goldenrecord.GoldenAttribute;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record GoldenRecordResponse(
        String entityType,
        UUID entityId,
        UUID partyId,
        UUID addressId,
        long version,
        String rulesetId,
        Instant projectedAt,
        List<AttributeResponse> attributes,
        List<UUID> sourceAssociationIds) {

    static GoldenRecordResponse from(GoldenRecordView view) {
        return new GoldenRecordResponse(
                view.entityType().name(), view.entityId(), view.partyId(), view.addressId(),
                view.version(), view.rulesetId().value(), view.projectedAt(), view.attributes().stream()
                        .map(AttributeResponse::from).toList(),
                view.sourceAssociations().stream().map(association -> association.value()).toList());
    }

    public record AttributeResponse(
            String name, String value, AttributeProvenanceResponse provenance) {

        static AttributeResponse from(GoldenAttribute attribute) {
            var provenance = attribute.provenance();
            return new AttributeResponse(
                    attribute.name().name(), attribute.value(), new AttributeProvenanceResponse(
                            SourceIdentityResponse.from(provenance.sourceRecord()),
                            provenance.associationId().value(), provenance.rule().name(),
                            provenance.rulesetId().value(), provenance.decidedAt()));
        }
    }

    public record AttributeProvenanceResponse(
            SourceIdentityResponse source,
            UUID associationId,
            String rule,
            String rulesetId,
            Instant decidedAt) {}
}
