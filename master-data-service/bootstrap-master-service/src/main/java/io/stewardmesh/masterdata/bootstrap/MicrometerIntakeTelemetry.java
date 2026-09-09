package io.stewardmesh.masterdata.bootstrap;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.stewardmesh.masterdata.application.port.out.IntakeTelemetry;
import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

final class MicrometerIntakeTelemetry implements IntakeTelemetry {

    private final MeterRegistry registry;

    MicrometerIntakeTelemetry(MeterRegistry registry) {
        this.registry = Objects.requireNonNull(registry, "registry must not be null");
    }

    @Override
    public <T> T measure(Stage stage, Supplier<T> operation) {
        Objects.requireNonNull(stage, "stage must not be null");
        Objects.requireNonNull(operation, "operation must not be null");
        Timer.Sample sample = Timer.start(registry);
        try {
            return operation.get();
        } catch (RuntimeException exception) {
            registry.counter("stewardmesh.imports.stage.failures", "stage", tag(stage)).increment();
            throw exception;
        } finally {
            sample.stop(registry.timer("stewardmesh.imports.stage.duration", "stage", tag(stage)));
        }
    }

    @Override
    public void recordValidationIssues(List<ValidationIssue> issues) {
        List.copyOf(issues).forEach(issue -> registry.counter(
                        "stewardmesh.imports.validation.issues",
                        "code",
                        issue.code().name().toLowerCase(Locale.ROOT))
                .increment());
    }

    private static String tag(Stage stage) {
        return stage.name().toLowerCase(Locale.ROOT);
    }
}
