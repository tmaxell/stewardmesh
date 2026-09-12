CREATE TABLE action_plan (
    plan_id UUID PRIMARY KEY,
    plan_version BIGINT NOT NULL,
    import_job_id UUID NOT NULL REFERENCES import_job (id),
    content_fingerprint VARCHAR(64) NOT NULL,
    plan_hash VARCHAR(64) NOT NULL UNIQUE,
    proposed_by_subject VARCHAR(128) NOT NULL,
    proposed_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(16) NOT NULL,
    risk VARCHAR(8) NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT action_plan_version_positive CHECK (plan_version > 0),
    CONSTRAINT action_plan_fingerprint_format CHECK (content_fingerprint ~ '^[0-9a-f]{64}$'),
    CONSTRAINT action_plan_hash_format CHECK (plan_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT action_plan_subject_nonblank CHECK (btrim(proposed_by_subject) <> ''),
    CONSTRAINT action_plan_status_valid CHECK (
        status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'EXECUTING', 'EXECUTED', 'FAILED')
    ),
    CONSTRAINT action_plan_risk_valid CHECK (risk IN ('LOW', 'MEDIUM', 'HIGH'))
);

-- At most one plan may await a decision for the same proposed content. Decided plans no longer
-- block a fresh proposal, so a rejected mutation can be re-proposed after it is corrected.
CREATE UNIQUE INDEX action_plan_undecided_content_idx
    ON action_plan (content_fingerprint)
    WHERE status = 'PROPOSED';

CREATE INDEX action_plan_import_idx
    ON action_plan (import_job_id, proposed_at, plan_id);

CREATE TABLE action_plan_step (
    plan_id UUID NOT NULL REFERENCES action_plan (plan_id),
    step_sequence INTEGER NOT NULL,
    action_type VARCHAR(32) NOT NULL,
    reason_code VARCHAR(64) NOT NULL,
    party_id UUID,
    expected_party_version BIGINT,
    site_id UUID,
    expected_site_version BIGINT,
    address_id UUID,
    procurement_business_unit_id UUID,
    assignment_id UUID,
    client_business_unit_id UUID,
    purposes VARCHAR(64),
    valid_from DATE,
    valid_to DATE,
    origin_system VARCHAR(128),
    source_record_id VARCHAR(128),
    source_version BIGINT,
    PRIMARY KEY (plan_id, step_sequence),
    CONSTRAINT action_plan_step_sequence_positive CHECK (step_sequence > 0),
    CONSTRAINT action_plan_step_type_valid CHECK (
        action_type IN (
            'CREATE_SUPPLIER_PARTY',
            'LINK_SOURCE_RECORD',
            'CREATE_SUPPLIER_SITE',
            'ASSIGN_SUPPLIER_SITE'
        )
    ),
    CONSTRAINT action_plan_step_reason_format CHECK (reason_code ~ '^[A-Z][A-Z0-9_]{0,63}$'),
    CONSTRAINT action_plan_step_purposes_format CHECK (
        purposes IS NULL
        OR purposes ~ '^(PAY|PURCHASING|SHIP_FROM|SOURCING)(,(PAY|PURCHASING|SHIP_FROM|SOURCING))*$'
    ),
    CONSTRAINT action_plan_step_interval_valid CHECK (
        valid_to IS NULL OR (valid_from IS NOT NULL AND valid_to >= valid_from)
    ),
    CONSTRAINT action_plan_step_versions_positive CHECK (
        (expected_party_version IS NULL OR expected_party_version > 0)
        AND (expected_site_version IS NULL OR expected_site_version > 0)
        AND (source_version IS NULL OR source_version > 0)
    ),
    -- Each action type owns an exact column shape; no step may carry a field its type never uses.
    CONSTRAINT action_plan_step_shape_valid CHECK (
        CASE action_type
            WHEN 'CREATE_SUPPLIER_PARTY' THEN
                party_id IS NOT NULL
                AND origin_system IS NOT NULL
                AND source_record_id IS NOT NULL
                AND source_version IS NOT NULL
                AND expected_party_version IS NULL
                AND site_id IS NULL
                AND expected_site_version IS NULL
                AND address_id IS NULL
                AND procurement_business_unit_id IS NULL
                AND assignment_id IS NULL
                AND client_business_unit_id IS NULL
                AND purposes IS NULL
                AND valid_from IS NULL
                AND valid_to IS NULL
            WHEN 'LINK_SOURCE_RECORD' THEN
                party_id IS NOT NULL
                AND expected_party_version IS NOT NULL
                AND origin_system IS NOT NULL
                AND source_record_id IS NOT NULL
                AND source_version IS NOT NULL
                AND site_id IS NULL
                AND expected_site_version IS NULL
                AND address_id IS NULL
                AND procurement_business_unit_id IS NULL
                AND assignment_id IS NULL
                AND client_business_unit_id IS NULL
                AND purposes IS NULL
                AND valid_from IS NULL
                AND valid_to IS NULL
            WHEN 'CREATE_SUPPLIER_SITE' THEN
                site_id IS NOT NULL
                AND party_id IS NOT NULL
                AND expected_party_version IS NOT NULL
                AND address_id IS NOT NULL
                AND procurement_business_unit_id IS NOT NULL
                AND expected_site_version IS NULL
                AND assignment_id IS NULL
                AND client_business_unit_id IS NULL
                AND purposes IS NULL
                AND valid_from IS NULL
                AND valid_to IS NULL
                AND origin_system IS NULL
                AND source_record_id IS NULL
                AND source_version IS NULL
            WHEN 'ASSIGN_SUPPLIER_SITE' THEN
                assignment_id IS NOT NULL
                AND site_id IS NOT NULL
                AND expected_site_version IS NOT NULL
                AND client_business_unit_id IS NOT NULL
                AND purposes IS NOT NULL
                AND valid_from IS NOT NULL
                AND party_id IS NULL
                AND expected_party_version IS NULL
                AND address_id IS NULL
                AND procurement_business_unit_id IS NULL
                AND origin_system IS NULL
                AND source_record_id IS NULL
                AND source_version IS NULL
            ELSE false
        END
    )
);

CREATE TABLE action_plan_step_evidence (
    plan_id UUID NOT NULL,
    step_sequence INTEGER NOT NULL,
    evidence_type VARCHAR(32) NOT NULL,
    evidence_reference VARCHAR(256) NOT NULL,
    evidence_version BIGINT NOT NULL,
    PRIMARY KEY (plan_id, step_sequence, evidence_type, evidence_reference, evidence_version),
    FOREIGN KEY (plan_id, step_sequence)
        REFERENCES action_plan_step (plan_id, step_sequence),
    CONSTRAINT action_plan_step_evidence_type_valid CHECK (
        evidence_type IN (
            'SOURCE_RECORD',
            'MATCH_EVALUATION',
            'GOLDEN_RECORD',
            'BUSINESS_UNIT_REFERENCE'
        )
    ),
    CONSTRAINT action_plan_step_evidence_reference_nonblank CHECK (
        btrim(evidence_reference) <> ''
    ),
    CONSTRAINT action_plan_step_evidence_version_positive CHECK (evidence_version > 0)
);

CREATE FUNCTION reject_action_plan_content_change()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    IF NEW.plan_id <> OLD.plan_id
        OR NEW.plan_version <> OLD.plan_version
        OR NEW.import_job_id <> OLD.import_job_id
        OR NEW.content_fingerprint <> OLD.content_fingerprint
        OR NEW.plan_hash <> OLD.plan_hash
        OR NEW.proposed_by_subject <> OLD.proposed_by_subject
        OR NEW.proposed_at <> OLD.proposed_at
        OR NEW.risk <> OLD.risk
    THEN
        RAISE EXCEPTION 'sealed action plan content cannot be changed';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE FUNCTION reject_invalid_action_plan_transition()
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
    RETURN NEW;
END;
$function$;

CREATE TRIGGER action_plan_content_immutable
    BEFORE UPDATE ON action_plan
    FOR EACH ROW EXECUTE FUNCTION reject_action_plan_content_change();

CREATE TRIGGER action_plan_transition_guard
    BEFORE UPDATE ON action_plan
    FOR EACH ROW EXECUTE FUNCTION reject_invalid_action_plan_transition();

CREATE TRIGGER action_plan_undeletable
    BEFORE DELETE ON action_plan
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TRIGGER action_plan_step_immutable
    BEFORE UPDATE OR DELETE ON action_plan_step
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TRIGGER action_plan_step_evidence_immutable
    BEFORE UPDATE OR DELETE ON action_plan_step_evidence
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();
