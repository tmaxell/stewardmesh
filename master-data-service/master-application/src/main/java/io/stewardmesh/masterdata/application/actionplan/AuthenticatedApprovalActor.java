package io.stewardmesh.masterdata.application.actionplan;

import java.util.Objects;

/** Server-derived identity and authority; neither field is accepted from model tool arguments. */
public record AuthenticatedApprovalActor(String subject, boolean authorizedToApprove) {

    private static final int MAX_SUBJECT_LENGTH = 128;

    public AuthenticatedApprovalActor {
        Objects.requireNonNull(subject, "subject must not be null");
        subject = subject.strip();
        if (subject.isEmpty() || subject.length() > MAX_SUBJECT_LENGTH) {
            throw new IllegalArgumentException(
                    "authenticated approval subject must contain 1 to 128 characters");
        }
    }
}
