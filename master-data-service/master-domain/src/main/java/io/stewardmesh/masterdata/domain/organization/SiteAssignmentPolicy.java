package io.stewardmesh.masterdata.domain.organization;

import java.util.Collection;
import java.util.Objects;

/** Deterministic guard against ambiguous effective authorizations. */
public final class SiteAssignmentPolicy {

    public void validate(SiteAssignment candidate, Collection<SiteAssignment> existing) {
        Objects.requireNonNull(candidate, "candidate must not be null");
        var current = ListSupport.copy(existing);
        if (current.stream().anyMatch(assignment -> assignment.id().equals(candidate.id()))) {
            throw new IllegalArgumentException("site assignment identity already exists");
        }
        if (current.stream().anyMatch(candidate::conflictsWith)) {
            throw new IllegalArgumentException("site assignment overlaps an existing authorization");
        }
    }

    private static final class ListSupport {
        private ListSupport() {}

        static java.util.List<SiteAssignment> copy(Collection<SiteAssignment> assignments) {
            var copy = java.util.List.copyOf(
                    Objects.requireNonNull(assignments, "existing must not be null"));
            if (copy.stream().anyMatch(Objects::isNull)) {
                throw new IllegalArgumentException("existing assignments must not contain nulls");
            }
            return copy;
        }
    }
}
