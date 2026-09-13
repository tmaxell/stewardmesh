package io.stewardmesh.masterdata.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.stewardmesh.masterdata.application.actionplan.ActionPlanExecution;
import io.stewardmesh.masterdata.application.actionplan.AuthenticatedExecutionActor;
import io.stewardmesh.masterdata.application.actionplan.ExecuteActionPlanCommand;
import io.stewardmesh.masterdata.application.port.in.ExecuteActionPlan;
import java.time.Clock;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

/**
 * Measures governed execution from outside its transaction. A replayed request returns the original
 * receipt, so a receipt stamped before this call started identifies an idempotent replay rather
 * than a second business effect.
 */
final class MeteredExecuteActionPlan implements ExecuteActionPlan {

    private final ExecuteActionPlan delegate;
    private final MeterRegistry registry;
    private final Clock clock;

    MeteredExecuteActionPlan(ExecuteActionPlan delegate, MeterRegistry registry, Clock clock) {
        this.delegate = Objects.requireNonNull(delegate, "delegate must not be null");
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public ActionPlanExecution execute(
            ExecuteActionPlanCommand command, AuthenticatedExecutionActor authenticatedActor) {
        var requestedAt = clock.instant().truncatedTo(ChronoUnit.MICROS);
        Timer.Sample sample = Timer.start(registry);
        try {
            ActionPlanExecution execution = delegate.execute(command, authenticatedActor);
            boolean replayed = execution.executedAt().isBefore(requestedAt);
            registry.counter(
                            "stewardmesh.actionplan.executions",
                            "outcome",
                            "succeeded",
                            "replayed",
                            Boolean.toString(replayed))
                    .increment();
            if (!replayed) {
                registry.summary("stewardmesh.actionplan.execution.effects")
                        .record(execution.effects().size());
            }
            return execution;
        } catch (RuntimeException failure) {
            registry.counter(
                            "stewardmesh.actionplan.executions",
                            "outcome",
                            "failed",
                            "replayed",
                            "false")
                    .increment();
            registry.counter(
                            "stewardmesh.actionplan.execution.failures",
                            "reason",
                            tag(failure.getClass().getSimpleName()))
                    .increment();
            throw failure;
        } finally {
            sample.stop(registry.timer("stewardmesh.actionplan.execution.duration"));
        }
    }

    private static String tag(String value) {
        return value.toLowerCase(Locale.ROOT);
    }
}
