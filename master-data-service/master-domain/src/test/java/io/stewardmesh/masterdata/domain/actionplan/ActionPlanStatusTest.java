package io.stewardmesh.masterdata.domain.actionplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

class ActionPlanStatusTest {

    private static final Map<ActionPlanStatus, Set<ActionPlanStatus>> ALLOWED = Map.of(
            ActionPlanStatus.PROPOSED,
            EnumSet.of(ActionPlanStatus.APPROVED, ActionPlanStatus.REJECTED),
            ActionPlanStatus.APPROVED,
            EnumSet.of(ActionPlanStatus.EXECUTING),
            ActionPlanStatus.REJECTED,
            EnumSet.noneOf(ActionPlanStatus.class),
            ActionPlanStatus.EXECUTING,
            EnumSet.of(ActionPlanStatus.EXECUTED, ActionPlanStatus.FAILED),
            ActionPlanStatus.EXECUTED,
            EnumSet.noneOf(ActionPlanStatus.class),
            ActionPlanStatus.FAILED,
            EnumSet.noneOf(ActionPlanStatus.class));

    @ParameterizedTest
    @EnumSource(ActionPlanStatus.class)
    void permitsExactlyTheGovernedTransitions(ActionPlanStatus current) {
        Set<ActionPlanStatus> allowed = ALLOWED.get(current);

        for (ActionPlanStatus requested : ActionPlanStatus.values()) {
            assertEquals(
                    allowed.contains(requested),
                    current.canTransitionTo(requested),
                    current + " -> " + requested);
        }
    }

    @ParameterizedTest
    @EnumSource(ActionPlanStatus.class)
    void neverTransitionsToItself(ActionPlanStatus current) {
        assertFalse(current.canTransitionTo(current));
    }

    @Test
    void treatsRejectedExecutedAndFailedAsTerminal() {
        assertTrue(ActionPlanStatus.REJECTED.isTerminal());
        assertTrue(ActionPlanStatus.EXECUTED.isTerminal());
        assertTrue(ActionPlanStatus.FAILED.isTerminal());
        assertFalse(ActionPlanStatus.PROPOSED.isTerminal());
        assertFalse(ActionPlanStatus.APPROVED.isTerminal());
        assertFalse(ActionPlanStatus.EXECUTING.isTerminal());
    }

    @Test
    void terminalStatusAcceptsNoFurtherTransition() {
        EnumSet.allOf(ActionPlanStatus.class).stream()
                .filter(ActionPlanStatus::isTerminal)
                .forEach(terminal -> EnumSet.allOf(ActionPlanStatus.class)
                        .forEach(requested -> assertFalse(terminal.canTransitionTo(requested))));
    }

    @Test
    void rejectsMissingRequestedStatus() {
        assertThrows(
                NullPointerException.class, () -> ActionPlanStatus.PROPOSED.canTransitionTo(null));
    }
}
