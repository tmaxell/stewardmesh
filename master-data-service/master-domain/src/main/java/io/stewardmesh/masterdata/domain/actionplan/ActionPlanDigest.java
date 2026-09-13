package io.stewardmesh.masterdata.domain.actionplan;

import io.stewardmesh.masterdata.domain.intake.ImportJobId;
import io.stewardmesh.masterdata.domain.intake.SourceRecordIdentity;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

final class ActionPlanDigest {

    private static final String SCHEMA = "stewardmesh.action-plan.sha256.v1";
    private static final String CONTENT_SCHEMA = "stewardmesh.action-plan-content.sha256.v1";

    private final MessageDigest digest;

    private ActionPlanDigest() {
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    static ActionPlanHash calculate(
            ActionPlanId id,
            ActionPlanVersion version,
            ImportJobId importId,
            Instant createdAt,
            String proposedBySubject,
            List<ActionPlanStep> steps) {
        ActionPlanDigest canonical = new ActionPlanDigest();
        canonical.add(SCHEMA);
        canonical.add(id.value().toString());
        canonical.add(version.value());
        canonical.add(importId.value().toString());
        canonical.add(createdAt.getEpochSecond());
        canonical.add(createdAt.getNano());
        canonical.add(proposedBySubject);
        canonical.add(steps.size());
        steps.forEach(canonical::addStep);
        return new ActionPlanHash(HexFormat.of().formatHex(canonical.digest.digest()));
    }

    static ActionPlanFingerprint fingerprint(ImportJobId importId, List<ActionPlanStep> steps) {
        ActionPlanDigest canonical = new ActionPlanDigest();
        canonical.add(CONTENT_SCHEMA);
        canonical.add(importId.value().toString());
        canonical.add(steps.size());
        steps.forEach(canonical::addStep);
        return new ActionPlanFingerprint(HexFormat.of().formatHex(canonical.digest.digest()));
    }

    private void addStep(ActionPlanStep step) {
        add(step.sequence());
        add(step.type().name());
        add(step.risk().name());
        add(step.reasonCode().value());

        switch (step) {
            case CreateSupplierPartyStep create -> {
                add(create.partyId().value().toString());
                add(create.sourceRecord());
            }
            case LinkSourceRecordStep link -> {
                add(link.sourceRecord());
                add(link.partyId().value().toString());
                add(link.expectedPartyVersion());
            }
            case CreateSupplierSiteStep create -> {
                add(create.siteId().value().toString());
                add(create.partyId().value().toString());
                add(create.expectedPartyVersion());
                add(create.addressId().value().toString());
                add(create.procurementBusinessUnitId().value().toString());
            }
            case AssignSupplierSiteStep assign -> {
                add(assign.assignmentId().value().toString());
                add(assign.siteId().value().toString());
                add(assign.expectedSiteVersion());
                add(assign.clientBusinessUnitId().value().toString());
                add(assign.purposes().size());
                assign.purposes().stream()
                        .map(Enum::name)
                        .sorted()
                        .forEach(this::add);
                add(assign.validFrom().toString());
                add(assign.validTo().map(Object::toString).orElse(""));
            }
        }

        add(step.evidence().size());
        step.evidence().forEach(reference -> {
            add(reference.type().name());
            add(reference.reference());
            add(reference.version());
        });
    }

    private void add(SourceRecordIdentity identity) {
        add(identity.originSystem().value());
        add(identity.sourceRecordId());
        add(identity.sourceVersion());
    }

    private void add(long value) {
        add(Long.toString(value));
    }

    private void add(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
