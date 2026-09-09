package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.domain.intake.ImportCounters;

public record ImportCountersResponse(
        int totalRows, int acceptedRows, int rejectedRows, int warningCount, int errorCount) {

    static ImportCountersResponse from(ImportCounters counters) {
        return new ImportCountersResponse(
                counters.totalRows(),
                counters.acceptedRows(),
                counters.rejectedRows(),
                counters.warningCount(),
                counters.errorCount());
    }
}
