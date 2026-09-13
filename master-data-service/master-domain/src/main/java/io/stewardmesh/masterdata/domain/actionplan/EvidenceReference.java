package io.stewardmesh.masterdata.domain.actionplan;

import java.util.Comparator;
import java.util.Objects;

/** Minimal evidence pointer; source payloads stay outside the governed plan. */
public record EvidenceReference(EvidenceType type, String reference, long version)
        implements Comparable<EvidenceReference> {

    private static final int MAX_REFERENCE_LENGTH = 256;
    private static final Comparator<EvidenceReference> ORDER = Comparator.<EvidenceReference, String>comparing(
                    evidence -> evidence.type().name())
            .thenComparing(EvidenceReference::reference)
            .thenComparingLong(EvidenceReference::version);

    public EvidenceReference {
        Objects.requireNonNull(type, "type must not be null");
        reference = ActionPlanText.requireNonBlank(
                reference, "evidence reference", MAX_REFERENCE_LENGTH);
        if (version <= 0) {
            throw new IllegalArgumentException("evidence version must be positive");
        }
    }

    @Override
    public int compareTo(EvidenceReference other) {
        return ORDER.compare(this, Objects.requireNonNull(other, "other must not be null"));
    }
}
