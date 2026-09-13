CREATE TABLE inbox_event (
    event_id UUID PRIMARY KEY,
    payload_fingerprint VARCHAR(64) NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    entity_version BIGINT NOT NULL,
    origin_system VARCHAR(64) NOT NULL,
    producer VARCHAR(64) NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID,
    received_at TIMESTAMPTZ NOT NULL,
    processing_status VARCHAR(24) NOT NULL DEFAULT 'RECEIVED',
    reason_code VARCHAR(64),
    completed_at TIMESTAMPTZ,
    CONSTRAINT inbox_fingerprint_format CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT inbox_entity_version_positive CHECK (entity_version > 0),
    CONSTRAINT inbox_status_valid CHECK (processing_status IN (
        'RECEIVED', 'ACCEPTED', 'LOOP_SUPPRESSED', 'REJECTED'
    ))
);

CREATE INDEX inbox_subject_version_idx
    ON inbox_event (origin_system, subject_type, subject_id, entity_version DESC);

CREATE TABLE quarantine_event (
    quarantine_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    payload_fingerprint VARCHAR(64) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    origin_system VARCHAR(64) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id VARCHAR(128) NOT NULL,
    quarantined_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT quarantine_fingerprint_format CHECK (payload_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT quarantine_reason_nonblank CHECK (btrim(reason_code) <> '')
);

CREATE INDEX quarantine_event_lookup_idx ON quarantine_event (event_id, quarantined_at DESC);

CREATE FUNCTION protect_integration_business_content()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    IF TG_OP = 'DELETE'
        OR (to_jsonb(NEW) - ARRAY['processing_status', 'reason_code', 'completed_at'])
            IS DISTINCT FROM
           (to_jsonb(OLD) - ARRAY['processing_status', 'reason_code', 'completed_at'])
    THEN
        RAISE EXCEPTION 'inbox business content is immutable';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER inbox_business_content_guard
    BEFORE UPDATE OR DELETE ON inbox_event
    FOR EACH ROW EXECUTE FUNCTION protect_integration_business_content();

CREATE TRIGGER quarantine_event_immutable
    BEFORE UPDATE OR DELETE ON quarantine_event
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();
