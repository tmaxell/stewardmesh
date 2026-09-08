CREATE TABLE intake_artifact (
    id UUID PRIMARY KEY,
    sha256 VARCHAR(64) NOT NULL UNIQUE,
    storage_key VARCHAR(1024) NOT NULL UNIQUE,
    content_type VARCHAR(255) NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT intake_artifact_sha256_format CHECK (sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE import_job (
    id UUID PRIMARY KEY,
    artifact_id UUID NOT NULL REFERENCES intake_artifact (id),
    source_system VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_rows INTEGER NOT NULL DEFAULT 0 CHECK (total_rows >= 0),
    accepted_rows INTEGER NOT NULL DEFAULT 0 CHECK (accepted_rows >= 0),
    rejected_rows INTEGER NOT NULL DEFAULT 0 CHECK (rejected_rows >= 0),
    warning_count INTEGER NOT NULL DEFAULT 0 CHECK (warning_count >= 0),
    error_count INTEGER NOT NULL DEFAULT 0 CHECK (error_count >= 0),
    failure_code VARCHAR(128),
    created_at TIMESTAMPTZ NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT import_job_status_valid CHECK (
        status IN ('RECEIVED', 'PARSING', 'PARSED', 'VALIDATING', 'VALIDATED', 'FAILED')
    ),
    CONSTRAINT import_job_rows_accounted CHECK (accepted_rows + rejected_rows <= total_rows),
    CONSTRAINT import_job_failure_consistent CHECK (
        (status = 'FAILED' AND failure_code IS NOT NULL)
        OR (status <> 'FAILED' AND failure_code IS NULL)
    )
);

CREATE INDEX import_job_source_system_idx ON import_job (source_system, created_at DESC);

CREATE TABLE idempotency_record (
    source_system VARCHAR(128) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    import_job_id UUID NOT NULL UNIQUE REFERENCES import_job (id),
    artifact_sha256 VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (source_system, idempotency_key),
    CONSTRAINT idempotency_artifact_sha256_format CHECK (artifact_sha256 ~ '^[0-9a-f]{64}$')
);

CREATE TABLE source_record (
    origin_system VARCHAR(128) NOT NULL,
    source_record_id VARCHAR(128) NOT NULL,
    source_version BIGINT NOT NULL CHECK (source_version > 0),
    import_job_id UUID NOT NULL REFERENCES import_job (id),
    ingested_at TIMESTAMPTZ NOT NULL,
    original_values JSONB NOT NULL CHECK (jsonb_typeof(original_values) = 'object'),
    canonical_values JSONB NOT NULL CHECK (jsonb_typeof(canonical_values) = 'object'),
    canonical_inn VARCHAR(12),
    canonical_kpp VARCHAR(9),
    canonical_ogrn VARCHAR(15),
    PRIMARY KEY (origin_system, source_record_id, source_version)
);

CREATE INDEX source_record_import_job_idx ON source_record (import_job_id);
CREATE INDEX source_record_inn_idx ON source_record (canonical_inn) WHERE canonical_inn IS NOT NULL;
CREATE INDEX source_record_kpp_idx ON source_record (canonical_kpp) WHERE canonical_kpp IS NOT NULL;
CREATE INDEX source_record_ogrn_idx ON source_record (canonical_ogrn) WHERE canonical_ogrn IS NOT NULL;

CREATE TABLE validation_issue (
    import_job_id UUID NOT NULL REFERENCES import_job (id),
    issue_index INTEGER NOT NULL CHECK (issue_index >= 0),
    code VARCHAR(64) NOT NULL,
    severity VARCHAR(8) GENERATED ALWAYS AS (
        CASE WHEN code = 'HEADER_UNKNOWN' THEN 'WARNING' ELSE 'ERROR' END
    ) STORED,
    row_number INTEGER CHECK (row_number > 0),
    field_name VARCHAR(128),
    parameters JSONB NOT NULL CHECK (jsonb_typeof(parameters) = 'object'),
    PRIMARY KEY (import_job_id, issue_index),
    CONSTRAINT validation_issue_code_valid CHECK (code IN (
        'WORKBOOK_FORMAT_INVALID',
        'WORKBOOK_LIMIT_EXCEEDED',
        'WORKBOOK_UNSAFE_CONTENT',
        'WORKBOOK_SHEET_MISSING',
        'WORKBOOK_UNEXPECTED_SHEET',
        'HEADER_MISSING',
        'HEADER_DUPLICATE',
        'HEADER_UNKNOWN',
        'FORMULA_CELL_NOT_ALLOWED',
        'REQUIRED_VALUE_MISSING',
        'VALUE_FORMAT_INVALID',
        'VALUE_TOO_LONG',
        'VALUE_NOT_ALLOWED',
        'CONDITIONAL_VALUE_MISSING'
    ))
);

CREATE INDEX validation_issue_report_idx
    ON validation_issue (import_job_id, issue_index);

CREATE FUNCTION reject_immutable_intake_row() RETURNS TRIGGER
LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION 'immutable intake row cannot be changed';
END;
$$;

CREATE TRIGGER intake_artifact_immutable
    BEFORE UPDATE OR DELETE ON intake_artifact
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TRIGGER idempotency_record_immutable
    BEFORE UPDATE OR DELETE ON idempotency_record
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TRIGGER source_record_immutable
    BEFORE UPDATE OR DELETE ON source_record
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TRIGGER validation_issue_immutable
    BEFORE UPDATE OR DELETE ON validation_issue
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();
