package io.stewardmesh.masterdata.rest;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.stewardmesh.masterdata.application.intake.ProcessSupplierImportResult;
import java.time.Duration;

final class IntakeApiTelemetry {

    private final MeterRegistry registry;
    private final Counter started;
    private final Counter replayed;
    private final DistributionSummary artifactBytes;
    private final DistributionSummary acceptedRows;
    private final DistributionSummary rejectedRows;
    private final Timer processingDuration;

    IntakeApiTelemetry(MeterRegistry registry) {
        this.registry = registry;
        started = Counter.builder("stewardmesh.imports.started").register(registry);
        replayed = Counter.builder("stewardmesh.imports.replayed").register(registry);
        artifactBytes = DistributionSummary.builder("stewardmesh.imports.artifact.bytes")
                .baseUnit("bytes")
                .register(registry);
        acceptedRows = DistributionSummary.builder("stewardmesh.imports.rows.accepted")
                .register(registry);
        rejectedRows = DistributionSummary.builder("stewardmesh.imports.rows.rejected")
                .register(registry);
        processingDuration = Timer.builder("stewardmesh.imports.processing")
                .register(registry);
    }

    long start() {
        return System.nanoTime();
    }

    void started(long bytes, boolean isReplay) {
        artifactBytes.record(bytes);
        if (isReplay) {
            replayed.increment();
        } else {
            started.increment();
        }
    }

    void completed(long startedAt, ProcessSupplierImportResult result) {
        processingDuration.record(Duration.ofNanos(System.nanoTime() - startedAt));
        acceptedRows.record(result.counters().acceptedRows());
        rejectedRows.record(result.counters().rejectedRows());
        registry.counter(
                        "stewardmesh.imports.completed",
                        "outcome",
                        result.status().name().toLowerCase(java.util.Locale.ROOT))
                .increment();
    }
}
