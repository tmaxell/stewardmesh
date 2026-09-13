CREATE TABLE action_plan_approval (
    plan_id UUID PRIMARY KEY REFERENCES action_plan (plan_id),
    plan_version BIGINT NOT NULL,
    plan_hash VARCHAR(64) NOT NULL,
    decision VARCHAR(8) NOT NULL,
    request_key VARCHAR(128) NOT NULL,
    decided_by_subject VARCHAR(128) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    reason VARCHAR(512) NOT NULL,
    CONSTRAINT action_plan_approval_version_positive CHECK (plan_version > 0),
    CONSTRAINT action_plan_approval_hash_format CHECK (plan_hash ~ '^[0-9a-f]{64}$'),
    CONSTRAINT action_plan_approval_decision_valid CHECK (decision IN ('APPROVE', 'REJECT')),
    CONSTRAINT action_plan_approval_request_key_nonblank CHECK (btrim(request_key) <> ''),
    CONSTRAINT action_plan_approval_subject_nonblank CHECK (btrim(decided_by_subject) <> ''),
    CONSTRAINT action_plan_approval_reason_nonblank CHECK (btrim(reason) <> ''),
    UNIQUE (decided_by_subject, request_key)
);

CREATE TRIGGER action_plan_approval_immutable
    BEFORE UPDATE OR DELETE ON action_plan_approval
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

-- A decided status is valid only when the same transaction already persisted the matching exact
-- human decision. This closes repository or operator paths that might otherwise skip policy.
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
            SELECT 1
            FROM action_plan_approval approval
            WHERE approval.plan_id = NEW.plan_id
              AND approval.plan_version = NEW.plan_version
              AND approval.plan_hash = NEW.plan_hash
              AND approval.decision = CASE NEW.status
                  WHEN 'APPROVED' THEN 'APPROVE'
                  ELSE 'REJECT'
              END
        )
    THEN
        RAISE EXCEPTION 'action plan decision requires an exact persisted approval record';
    END IF;
    RETURN NEW;
END;
$function$;
