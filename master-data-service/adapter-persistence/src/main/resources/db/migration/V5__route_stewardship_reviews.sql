ALTER TABLE import_job
    DROP CONSTRAINT import_job_status_valid;

ALTER TABLE import_job
    ADD CONSTRAINT import_job_status_valid CHECK (
        status IN (
            'RECEIVED',
            'PARSING',
            'PARSED',
            'VALIDATING',
            'VALIDATED',
            'MATCHING',
            'MATCHED',
            'REVIEW_REQUIRED',
            'FAILED'
        )
    );

CREATE TABLE stewardship_case (
    origin_system VARCHAR(128) NOT NULL,
    source_record_id VARCHAR(128) NOT NULL,
    source_version BIGINT NOT NULL,
    ruleset_id VARCHAR(64) NOT NULL,
    reason VARCHAR(32) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    opened_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (origin_system, source_record_id, source_version, ruleset_id),
    FOREIGN KEY (origin_system, source_record_id, source_version, ruleset_id)
        REFERENCES match_evaluation (
            origin_system,
            source_record_id,
            source_version,
            ruleset_id
        ),
    CONSTRAINT stewardship_case_reason_valid CHECK (
        reason IN ('AMBIGUOUS_MATCH', 'AUTHORITATIVE_CONFLICT')
    ),
    CONSTRAINT stewardship_case_status_valid CHECK (status = 'OPEN')
);

CREATE INDEX stewardship_case_opened_idx
    ON stewardship_case (status, opened_at, origin_system, source_record_id);

CREATE TRIGGER stewardship_case_immutable
    BEFORE UPDATE OR DELETE ON stewardship_case
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();
