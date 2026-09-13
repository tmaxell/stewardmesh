package io.stewardmesh.masterdata.application.actionplan;

import java.util.Objects;

/** Authenticated subject injected by the server, never accepted from model tool arguments. */
public record AuthenticatedProposalActor(String subject) {

    private static final int MAX_SUBJECT_LENGTH = 128;

    public AuthenticatedProposalActor {
        Objects.requireNonNull(subject, "subject must not be null");
        if (subject.isBlank()) {
            throw new IllegalArgumentException("subject must not be blank");
        }
        if (subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException("subject must not exceed 128 characters");
        }
    }
}
