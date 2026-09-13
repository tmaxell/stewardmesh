package io.stewardmesh.masterdata.application.actionplan;

import java.util.Objects;

/** Server-derived execution identity and authorization result. */
public record AuthenticatedExecutionActor(String subject, boolean authorizedToExecute) {

    public AuthenticatedExecutionActor {
        Objects.requireNonNull(subject, "subject must not be null");
        subject = subject.strip();
        if (subject.isEmpty() || subject.length() > 128) {
            throw new IllegalArgumentException(
                    "authenticated execution subject must contain 1 to 128 characters");
        }
    }
}
