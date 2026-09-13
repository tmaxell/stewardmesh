CREATE TABLE action_plan_execution (
    execution_id UUID PRIMARY KEY,
    plan_id UUID NOT NULL UNIQUE REFERENCES action_plan (plan_id),
    plan_version BIGINT NOT NULL,
    plan_hash VARCHAR(64) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    executed_by_subject VARCHAR(128) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    executed_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    UNIQUE (executed_by_subject, request_key),
    CONSTRAINT action_plan_execution_version_positive CHECK (plan_version > 0),
    CONSTRAINT action_plan_execution_hash_format CHECK (plan_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT action_plan_execution_request_nonblank CHECK (btrim(request_key) <> ''),
    CONSTRAINT action_plan_execution_subject_nonblank CHECK (btrim(executed_by_subject) <> ''),
    CONSTRAINT action_plan_execution_reason_nonblank CHECK (btrim(reason) <> '')
);

CREATE TABLE action_plan_execution_effect (
    execution_id UUID NOT NULL REFERENCES action_plan_execution (execution_id),
    step_sequence INTEGER NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id UUID NOT NULL,
    subject_version BIGINT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    PRIMARY KEY (execution_id, step_sequence),
    CONSTRAINT execution_effect_sequence_positive CHECK (step_sequence > 0),
    CONSTRAINT execution_effect_subject_version_positive CHECK (subject_version > 0),
    CONSTRAINT execution_effect_action_type_valid CHECK (action_type IN (
        'CREATE_SUPPLIER_PARTY', 'LINK_SOURCE_RECORD',
        'CREATE_SUPPLIER_SITE', 'ASSIGN_SUPPLIER_SITE'
    ))
);

CREATE TABLE audit_event (
    audit_id UUID PRIMARY KEY,
    plan_id UUID NOT NULL REFERENCES action_plan (plan_id),
    plan_version BIGINT NOT NULL,
    plan_hash VARCHAR(64) NOT NULL,
    actor_subject VARCHAR(128) NOT NULL,
    action VARCHAR(64) NOT NULL,
    result VARCHAR(32) NOT NULL,
    reason VARCHAR(512) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    affected_entities UUID[] NOT NULL,
    CONSTRAINT audit_plan_version_positive CHECK (plan_version > 0),
    CONSTRAINT audit_plan_hash_format CHECK (plan_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT audit_actor_nonblank CHECK (btrim(actor_subject) <> ''),
    CONSTRAINT audit_action_nonblank CHECK (btrim(action) <> ''),
    CONSTRAINT audit_result_nonblank CHECK (btrim(result) <> ''),
    CONSTRAINT audit_reason_nonblank CHECK (btrim(reason) <> '')
);

CREATE TABLE outbox_event (
    event_id UUID PRIMARY KEY,
    event_type VARCHAR(64) NOT NULL,
    schema_version INTEGER NOT NULL,
    subject_type VARCHAR(32) NOT NULL,
    subject_id UUID NOT NULL,
    entity_version BIGINT NOT NULL,
    origin_system VARCHAR(64) NOT NULL,
    producer VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    correlation_id UUID NOT NULL,
    causation_id UUID NOT NULL,
    payload JSONB NOT NULL,
    publication_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    publish_attempts INTEGER NOT NULL DEFAULT 0,
    claimed_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    broker_message_id VARCHAR(256),
    CONSTRAINT outbox_schema_version_positive CHECK (schema_version > 0),
    CONSTRAINT outbox_entity_version_positive CHECK (entity_version > 0),
    CONSTRAINT outbox_status_valid CHECK (publication_status IN ('PENDING', 'CLAIMED', 'PUBLISHED')),
    CONSTRAINT outbox_attempts_nonnegative CHECK (publish_attempts >= 0),
    CONSTRAINT outbox_payload_object CHECK (jsonb_typeof(payload) = 'object')
);

CREATE INDEX outbox_pending_idx
    ON outbox_event (occurred_at, event_id)
    WHERE publication_status <> 'PUBLISHED';

CREATE TRIGGER action_plan_execution_immutable
    BEFORE UPDATE OR DELETE ON action_plan_execution
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TRIGGER action_plan_execution_effect_immutable
    BEFORE UPDATE OR DELETE ON action_plan_execution_effect
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TRIGGER audit_event_immutable
    BEFORE UPDATE OR DELETE ON audit_event
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE FUNCTION protect_outbox_business_content()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    IF TG_OP = 'DELETE'
        OR (to_jsonb(NEW) - ARRAY[
            'publication_status', 'publish_attempts', 'claimed_at',
            'published_at', 'broker_message_id'
        ]) IS DISTINCT FROM (to_jsonb(OLD) - ARRAY[
            'publication_status', 'publish_attempts', 'claimed_at',
            'published_at', 'broker_message_id'
        ])
    THEN
        RAISE EXCEPTION 'outbox business content is immutable';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER outbox_business_content_guard
    BEFORE UPDATE OR DELETE ON outbox_event
    FOR EACH ROW EXECUTE FUNCTION protect_outbox_business_content();

CREATE OR REPLACE FUNCTION reject_invalid_action_plan_transition()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.status <> OLD.status
        AND NOT (
            (OLD.status = 'PROPOSED' AND NEW.status IN ('APPROVED', 'REJECTED'))
            OR (OLD.status = 'APPROVED' AND NEW.status = 'EXECUTING')
            OR (OLD.status = 'EXECUTING' AND NEW.status IN ('EXECUTED', 'FAILED'))
        )
    THEN
        RAISE EXCEPTION 'cannot transition action plan from % to %', OLD.status, NEW.status;
    END IF;

    IF OLD.status = 'PROPOSED' AND NEW.status IN ('APPROVED', 'REJECTED')
        AND NOT EXISTS (
            SELECT 1 FROM action_plan_approval approval
            WHERE approval.plan_id = NEW.plan_id
              AND approval.plan_version = NEW.plan_version
              AND approval.plan_hash = NEW.plan_hash
              AND approval.decision = CASE NEW.status
                  WHEN 'APPROVED' THEN 'APPROVE' ELSE 'REJECT' END
        )
    THEN
        RAISE EXCEPTION 'action plan decision requires an exact persisted approval record';
    END IF;

    IF OLD.status = 'EXECUTING' AND NEW.status = 'EXECUTED'
        AND NOT EXISTS (
            SELECT 1 FROM action_plan_execution execution
            WHERE execution.plan_id = NEW.plan_id
              AND execution.plan_version = NEW.plan_version
              AND execution.plan_hash = NEW.plan_hash
        )
    THEN
        RAISE EXCEPTION 'executed action plan requires an exact execution receipt';
    END IF;
    RETURN NEW;
END;
$function$;
