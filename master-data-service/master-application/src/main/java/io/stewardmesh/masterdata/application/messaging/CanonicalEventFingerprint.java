package io.stewardmesh.masterdata.application.messaging;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CanonicalEventFingerprint {
    private CanonicalEventFingerprint() {}

    public static String of(CanonicalEventEnvelope envelope) {
        var canonical = new StringBuilder()
                .append(envelope.eventType()).append('\n')
                .append(envelope.schemaVersion()).append('\n')
                .append(envelope.subjectType()).append('\n')
                .append(envelope.subjectId()).append('\n')
                .append(envelope.entityVersion()).append('\n')
                .append(envelope.originSystem()).append('\n');
        envelope.payload().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> canonical.append(entry.getKey()).append('=').append(entry.getValue()).append('\n'));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
