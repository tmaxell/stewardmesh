package io.stewardmesh.masterdata.domain.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ImportJobTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-28T10:15:30Z");

    @Test
    void followsTheCompleteValidationLifecycle() {
        var received = newJob();

        var validated = received.startParsing()
                .finishParsing(3)
                .startValidation()
                .finishValidation(2, 1, 1, 2);

        assertEquals(ImportStatus.VALIDATED, validated.status());
        assertEquals(new ImportCounters(3, 2, 1, 1, 2), validated.counters());
        assertTrue(validated.status().isTerminal());
        assertTrue(validated.failureCode().isEmpty());
        assertEquals(ImportStatus.RECEIVED, received.status(), "transitions must not mutate prior versions");
    }

    @Test
    void rejectsSkippedAndTerminalTransitions() {
        var received = newJob();

        assertThrows(InvalidImportTransitionException.class, received::startValidation);
        var validated = received.startParsing()
                .finishParsing(0)
                .startValidation()
                .finishValidation(0, 0, 0, 0);
        assertThrows(InvalidImportTransitionException.class, validated::startParsing);
        assertThrows(InvalidImportTransitionException.class, () -> validated.fail("LATE_FAILURE"));
    }

    @Test
    void canFailFromEveryNonTerminalStateButNotFromATerminalState() {
        var received = newJob();
        var states = new ImportJob[] {
            received,
            received.startParsing(),
            received.startParsing().finishParsing(2),
            received.startParsing().finishParsing(2).startValidation()
        };

        for (var state : states) {
            var failed = state.fail("PROCESSING_FAILED");
            assertEquals(ImportStatus.FAILED, failed.status());
            assertEquals("PROCESSING_FAILED", failed.failureCode().orElseThrow());
        }
        assertThrows(InvalidImportTransitionException.class, () -> states[0].fail("FIRST").fail("SECOND"));
    }

    @Test
    void requiresValidatedCountersToAccountForEveryParsedRow() {
        var validating = newJob().startParsing().finishParsing(3).startValidation();

        assertThrows(
                IllegalArgumentException.class, () -> validating.finishValidation(1, 1, 0, 0));
        assertThrows(
                IllegalArgumentException.class, () -> validating.finishValidation(3, 1, 0, 0));
    }

    private static ImportJob newJob() {
        return ImportJob.received(
                new ImportJobId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10b")),
                new IntakeArtifactId(UUID.fromString("018f3f70-79b2-7d6a-bf40-3d52dc2bb10c")),
                new SourceSystemRef("SYNTHETIC_ERP"),
                CREATED_AT);
    }
}
