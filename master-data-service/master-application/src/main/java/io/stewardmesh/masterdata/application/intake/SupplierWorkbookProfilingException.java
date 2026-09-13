package io.stewardmesh.masterdata.application.intake;

import io.stewardmesh.masterdata.domain.intake.ValidationCode;
import java.util.Objects;

/** Stable failure raised when a workbook cannot be profiled within the published safety contract. */
public final class SupplierWorkbookProfilingException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final ValidationCode code;

    public SupplierWorkbookProfilingException(ValidationCode code) {
        super(Objects.requireNonNull(code, "code must not be null").name());
        this.code = code;
    }

    public SupplierWorkbookProfilingException(ValidationCode code, Throwable cause) {
        super(Objects.requireNonNull(code, "code must not be null").name(), cause);
        this.code = code;
    }

    public ValidationCode code() {
        return code;
    }
}
