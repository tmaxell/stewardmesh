package io.stewardmesh.masterdata.domain.intake;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/** Structured validation evidence; parameters must already be safe for bounded reporting. */
public record ValidationIssue(
        ValidationCode code, Integer rowNumber, String field, Map<String, String> parameters) {

    private static final int MAX_FIELD_LENGTH = 128;
    private static final int MAX_PARAMETERS = 8;
    private static final int MAX_PARAMETER_LENGTH = 256;

    public ValidationIssue {
        Objects.requireNonNull(code, "code must not be null");
        if (rowNumber != null && rowNumber < 1) {
            throw new IllegalArgumentException("row number must be positive");
        }
        if (field != null) {
            field = DomainText.requireNonBlank(field, "field", MAX_FIELD_LENGTH);
        }
        parameters = Map.copyOf(Objects.requireNonNull(parameters, "parameters must not be null"));
        if (parameters.size() > MAX_PARAMETERS) {
            throw new IllegalArgumentException("validation issue has too many parameters");
        }
        parameters.forEach((key, value) -> {
            DomainText.requireNonBlank(key, "parameter key", MAX_FIELD_LENGTH);
            Objects.requireNonNull(value, "parameter value must not be null");
            if (value.length() > MAX_PARAMETER_LENGTH) {
                throw new IllegalArgumentException("validation parameter value is too long");
            }
        });
    }

    public ValidationSeverity severity() {
        return code.severity();
    }

    public Optional<Integer> row() {
        return Optional.ofNullable(rowNumber);
    }

    public Optional<String> fieldName() {
        return Optional.ofNullable(field);
    }
}
