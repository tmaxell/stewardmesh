package io.stewardmesh.masterdata.application.port.out;

import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import java.util.List;
import java.util.function.Supplier;

/** Records bounded operational evidence without coupling use cases to a metrics framework. */
public interface IntakeTelemetry {

    <T> T measure(Stage stage, Supplier<T> operation);

    void recordValidationIssues(List<ValidationIssue> issues);

    enum Stage {
        ARTIFACT_STORE,
        ARTIFACT_LOAD,
        WORKBOOK_PARSE,
        PERSISTENCE_BATCH
    }
}
