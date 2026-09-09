package io.stewardmesh.masterdata.rest;

import io.stewardmesh.masterdata.domain.intake.ValidationIssue;
import io.stewardmesh.masterdata.domain.intake.ValidationSeverity;
import java.util.Map;

public record ValidationIssueResponse(
        String code,
        ValidationSeverity severity,
        Integer row,
        String field,
        Map<String, String> parameters) {

    static ValidationIssueResponse from(ValidationIssue issue) {
        return new ValidationIssueResponse(
                issue.code().name(),
                issue.severity(),
                issue.rowNumber(),
                issue.field(),
                issue.parameters());
    }
}
