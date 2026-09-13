package io.stewardmesh.masterdata.domain.actionplan;

import java.util.List;
import java.util.Objects;

final class ActionPlanStepSupport {

    private static final int MAX_EVIDENCE_PER_STEP = 16;

    private ActionPlanStepSupport() {}

    static int requireSequence(int sequence) {
        if (sequence <= 0) {
            throw new IllegalArgumentException("step sequence must be positive");
        }
        return sequence;
    }

    static long requireExpectedVersion(long version, String aggregate) {
        if (version <= 0) {
            throw new IllegalArgumentException("expected " + aggregate + " version must be positive");
        }
        return version;
    }

    static List<EvidenceReference> requireEvidence(List<EvidenceReference> evidence) {
        Objects.requireNonNull(evidence, "evidence must not be null");
        if (evidence.isEmpty() || evidence.size() > MAX_EVIDENCE_PER_STEP) {
            throw new IllegalArgumentException("step evidence count must be between 1 and 16");
        }
        List<EvidenceReference> ordered = evidence.stream()
                .map(item -> Objects.requireNonNull(item, "evidence item must not be null"))
                .sorted()
                .toList();
        if (ordered.stream().distinct().count() != ordered.size()) {
            throw new IllegalArgumentException("step evidence must not contain duplicates");
        }
        return ordered;
    }
}
