CREATE TABLE business_unit (
    business_unit_id UUID PRIMARY KEY,
    code VARCHAR(64) NOT NULL UNIQUE,
    display_name VARCHAR(256) NOT NULL,
    roles VARCHAR(32) NOT NULL,
    reference_version BIGINT NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    lock_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT business_unit_code_format CHECK (code ~ '^[A-Z0-9][A-Z0-9._-]{0,63}$'),
    CONSTRAINT business_unit_name_nonblank CHECK (btrim(display_name) <> ''),
    CONSTRAINT business_unit_roles_valid CHECK (
        roles IN ('CLIENT', 'PROCUREMENT', 'CLIENT,PROCUREMENT')
    ),
    CONSTRAINT business_unit_version_positive CHECK (reference_version > 0),
    CONSTRAINT business_unit_interval_valid CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE TABLE business_unit_version (
    business_unit_id UUID NOT NULL,
    reference_version BIGINT NOT NULL,
    code VARCHAR(64) NOT NULL,
    display_name VARCHAR(256) NOT NULL,
    roles VARCHAR(32) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (business_unit_id, reference_version)
);

CREATE FUNCTION record_business_unit_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    INSERT INTO business_unit_version
        (business_unit_id, reference_version, code, display_name, roles, valid_from, valid_to)
    VALUES
        (NEW.business_unit_id, NEW.reference_version, NEW.code, NEW.display_name,
         NEW.roles, NEW.valid_from, NEW.valid_to);
    RETURN NEW;
END;
$function$;

CREATE TRIGGER business_unit_record_version
    AFTER INSERT OR UPDATE ON business_unit
    FOR EACH ROW EXECUTE FUNCTION record_business_unit_version();

CREATE TRIGGER business_unit_version_immutable
    BEFORE UPDATE OR DELETE ON business_unit_version
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TABLE site_assignment (
    assignment_id UUID PRIMARY KEY,
    site_entity_type VARCHAR(8) NOT NULL DEFAULT 'SITE',
    site_id UUID NOT NULL,
    client_business_unit_id UUID NOT NULL,
    purposes VARCHAR(64) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    assignment_version BIGINT NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    FOREIGN KEY (site_entity_type, site_id)
        REFERENCES golden_record_metadata (entity_type, entity_id),
    FOREIGN KEY (client_business_unit_id)
        REFERENCES business_unit (business_unit_id),
    CONSTRAINT site_assignment_entity_type CHECK (site_entity_type = 'SITE'),
    CONSTRAINT site_assignment_purposes_format CHECK (
        purposes ~ '^(PAY|PURCHASING|SHIP_FROM|SOURCING)(,(PAY|PURCHASING|SHIP_FROM|SOURCING))*$'
    ),
    CONSTRAINT site_assignment_version_positive CHECK (assignment_version > 0),
    CONSTRAINT site_assignment_interval_valid CHECK (valid_to IS NULL OR valid_to >= valid_from)
);

CREATE INDEX site_assignment_context_idx
    ON site_assignment (site_id, client_business_unit_id, valid_from, assignment_id);

CREATE FUNCTION reject_overlapping_site_assignment()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM site_assignment current_assignment
        WHERE current_assignment.assignment_id <> NEW.assignment_id
          AND current_assignment.site_id = NEW.site_id
          AND current_assignment.client_business_unit_id = NEW.client_business_unit_id
          AND string_to_array(current_assignment.purposes, ',')
                && string_to_array(NEW.purposes, ',')
          AND daterange(
                current_assignment.valid_from,
                COALESCE(current_assignment.valid_to, 'infinity'::date),
                '[]')
              && daterange(NEW.valid_from, COALESCE(NEW.valid_to, 'infinity'::date), '[]')
    ) THEN
        RAISE EXCEPTION 'site assignment overlaps an existing authorization';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER site_assignment_overlap_guard
    BEFORE INSERT OR UPDATE ON site_assignment
    FOR EACH ROW EXECUTE FUNCTION reject_overlapping_site_assignment();

CREATE TABLE site_assignment_version (
    assignment_id UUID NOT NULL,
    assignment_version BIGINT NOT NULL,
    site_id UUID NOT NULL,
    client_business_unit_id UUID NOT NULL,
    purposes VARCHAR(64) NOT NULL,
    valid_from DATE NOT NULL,
    valid_to DATE,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (assignment_id, assignment_version)
);

CREATE FUNCTION record_site_assignment_version()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    INSERT INTO site_assignment_version
        (assignment_id, assignment_version, site_id, client_business_unit_id,
         purposes, valid_from, valid_to)
    VALUES
        (NEW.assignment_id, NEW.assignment_version, NEW.site_id, NEW.client_business_unit_id,
         NEW.purposes, NEW.valid_from, NEW.valid_to);
    RETURN NEW;
END;
$function$;

CREATE TRIGGER site_assignment_record_version
    AFTER INSERT OR UPDATE ON site_assignment
    FOR EACH ROW EXECUTE FUNCTION record_site_assignment_version();

CREATE TRIGGER site_assignment_version_immutable
    BEFORE UPDATE OR DELETE ON site_assignment_version
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();
