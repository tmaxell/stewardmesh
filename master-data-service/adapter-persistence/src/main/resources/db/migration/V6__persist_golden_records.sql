CREATE TABLE golden_record_metadata (
    entity_type VARCHAR(8) NOT NULL,
    entity_id UUID NOT NULL,
    party_id UUID NOT NULL,
    address_id UUID,
    projection_version BIGINT NOT NULL,
    survivorship_ruleset VARCHAR(64) NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL,
    lock_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (entity_type, entity_id),
    CONSTRAINT golden_metadata_type_valid CHECK (entity_type IN ('PARTY', 'ADDRESS', 'SITE')),
    CONSTRAINT golden_metadata_version_positive CHECK (projection_version > 0),
    CONSTRAINT golden_metadata_ruleset_format CHECK (
        survivorship_ruleset ~ '^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$'
    ),
    CONSTRAINT golden_metadata_identity_shape CHECK (
        (entity_type = 'PARTY' AND entity_id = party_id AND address_id IS NULL)
        OR (entity_type = 'ADDRESS' AND entity_id = address_id)
        OR (entity_type = 'SITE' AND address_id IS NOT NULL)
    )
);

CREATE TABLE source_association (
    association_id UUID PRIMARY KEY,
    origin_system VARCHAR(128) NOT NULL,
    source_record_id VARCHAR(128) NOT NULL,
    source_version BIGINT NOT NULL,
    party_id UUID NOT NULL,
    address_id UUID,
    site_id UUID,
    party_match_ruleset VARCHAR(64) NOT NULL,
    site_match_ruleset VARCHAR(64),
    linked_at TIMESTAMPTZ NOT NULL,
    unlinked_at TIMESTAMPTZ,
    UNIQUE (association_id, origin_system, source_record_id, source_version),
    FOREIGN KEY (origin_system, source_record_id, source_version)
        REFERENCES source_record (origin_system, source_record_id, source_version),
    FOREIGN KEY (origin_system, source_record_id, source_version, party_match_ruleset)
        REFERENCES match_evaluation (
            origin_system, source_record_id, source_version, ruleset_id
        ),
    FOREIGN KEY (origin_system, source_record_id, source_version, site_match_ruleset)
        REFERENCES match_evaluation (
            origin_system, source_record_id, source_version, ruleset_id
        ),
    CONSTRAINT source_association_party_ruleset_format CHECK (
        party_match_ruleset ~ '^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$'
    ),
    CONSTRAINT source_association_site_ruleset_format CHECK (
        site_match_ruleset IS NULL
        OR site_match_ruleset ~ '^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$'
    ),
    CONSTRAINT source_association_site_shape CHECK (
        (site_id IS NULL AND site_match_ruleset IS NULL)
        OR (site_id IS NOT NULL AND address_id IS NOT NULL AND site_match_ruleset IS NOT NULL)
    ),
    CONSTRAINT source_association_interval_valid CHECK (
        unlinked_at IS NULL OR unlinked_at > linked_at
    )
);

CREATE UNIQUE INDEX source_association_active_target_idx
    ON source_association (
        origin_system,
        source_record_id,
        source_version,
        party_id,
        COALESCE(address_id, '00000000-0000-0000-0000-000000000000'::uuid),
        COALESCE(site_id, '00000000-0000-0000-0000-000000000000'::uuid)
    )
    WHERE unlinked_at IS NULL;

CREATE TABLE golden_record_version (
    entity_type VARCHAR(8) NOT NULL,
    entity_id UUID NOT NULL,
    projection_version BIGINT NOT NULL,
    party_id UUID NOT NULL,
    address_id UUID,
    survivorship_ruleset VARCHAR(64) NOT NULL,
    projected_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (entity_type, entity_id, projection_version),
    UNIQUE (entity_type, entity_id, projection_version, survivorship_ruleset),
    FOREIGN KEY (entity_type, entity_id)
        REFERENCES golden_record_metadata (entity_type, entity_id),
    CONSTRAINT golden_version_type_valid CHECK (entity_type IN ('PARTY', 'ADDRESS', 'SITE')),
    CONSTRAINT golden_version_number_positive CHECK (projection_version > 0),
    CONSTRAINT golden_version_ruleset_format CHECK (
        survivorship_ruleset ~ '^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$'
    ),
    CONSTRAINT golden_version_identity_shape CHECK (
        (entity_type = 'PARTY' AND entity_id = party_id AND address_id IS NULL)
        OR (entity_type = 'ADDRESS' AND entity_id = address_id)
        OR (entity_type = 'SITE' AND address_id IS NOT NULL)
    )
);

CREATE TABLE golden_attribute (
    entity_type VARCHAR(8) NOT NULL,
    entity_id UUID NOT NULL,
    projection_version BIGINT NOT NULL,
    attribute_name VARCHAR(48) NOT NULL,
    attribute_value VARCHAR(2048) NOT NULL,
    origin_system VARCHAR(128) NOT NULL,
    source_record_id VARCHAR(128) NOT NULL,
    source_version BIGINT NOT NULL,
    association_id UUID NOT NULL,
    survivorship_rule VARCHAR(32) NOT NULL,
    survivorship_ruleset VARCHAR(64) NOT NULL,
    decided_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (entity_type, entity_id, projection_version, attribute_name),
    FOREIGN KEY (entity_type, entity_id, projection_version, survivorship_ruleset)
        REFERENCES golden_record_version (
            entity_type, entity_id, projection_version, survivorship_ruleset
        ),
    FOREIGN KEY (association_id, origin_system, source_record_id, source_version)
        REFERENCES source_association (
            association_id, origin_system, source_record_id, source_version
        ),
    CONSTRAINT golden_attribute_type_valid CHECK (entity_type IN ('PARTY', 'ADDRESS', 'SITE')),
    CONSTRAINT golden_attribute_name_valid CHECK (attribute_name IN (
        'LEGAL_NAME', 'INN', 'OGRN',
        'COUNTRY_CODE', 'POSTAL_CODE', 'REGION', 'CITY', 'ADDRESS_LINE',
        'KPP', 'SITE_CODE', 'PROCUREMENT_BUSINESS_UNIT_CODE', 'SITE_PURPOSE'
    )),
    CONSTRAINT golden_attribute_entity_valid CHECK (
        (entity_type = 'PARTY' AND attribute_name IN ('LEGAL_NAME', 'INN', 'OGRN'))
        OR (entity_type = 'ADDRESS' AND attribute_name IN (
            'COUNTRY_CODE', 'POSTAL_CODE', 'REGION', 'CITY', 'ADDRESS_LINE'
        ))
        OR (entity_type = 'SITE' AND attribute_name IN (
            'KPP', 'SITE_CODE', 'PROCUREMENT_BUSINESS_UNIT_CODE', 'SITE_PURPOSE'
        ))
    ),
    CONSTRAINT golden_attribute_value_nonblank CHECK (btrim(attribute_value) <> ''),
    CONSTRAINT golden_attribute_rule_valid CHECK (survivorship_rule IN (
        'TRUSTED_SOURCE', 'MOST_RECENT_VERIFIED', 'MOST_COMPLETE',
        'SOURCE_PRIORITY_FALLBACK', 'DETERMINISTIC_TIE_BREAK'
    )),
    CONSTRAINT golden_attribute_ruleset_format CHECK (
        survivorship_ruleset ~ '^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$'
    )
);

CREATE INDEX golden_attribute_source_idx
    ON golden_attribute (origin_system, source_record_id, source_version);

CREATE FUNCTION protect_source_association()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $function$
BEGIN
    IF TG_OP = 'DELETE' THEN
        RAISE EXCEPTION 'source association history is immutable';
    END IF;
    IF OLD.unlinked_at IS NOT NULL
        OR NEW.unlinked_at IS NULL
        OR NEW.unlinked_at <= OLD.linked_at
        OR (to_jsonb(NEW) - 'unlinked_at') IS DISTINCT FROM (to_jsonb(OLD) - 'unlinked_at') THEN
        RAISE EXCEPTION 'only ending an active source association is allowed';
    END IF;
    RETURN NEW;
END;
$function$;

CREATE TRIGGER source_association_history_guard
    BEFORE UPDATE OR DELETE ON source_association
    FOR EACH ROW EXECUTE FUNCTION protect_source_association();

CREATE TRIGGER golden_record_version_immutable
    BEFORE UPDATE OR DELETE ON golden_record_version
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TRIGGER golden_attribute_immutable
    BEFORE UPDATE OR DELETE ON golden_attribute
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();
