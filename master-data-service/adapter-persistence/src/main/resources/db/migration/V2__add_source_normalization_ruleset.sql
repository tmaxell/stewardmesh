ALTER TABLE source_record
    ADD COLUMN normalization_ruleset VARCHAR(64) NOT NULL DEFAULT 'supplier-source-v1';

ALTER TABLE source_record
    ALTER COLUMN normalization_ruleset DROP DEFAULT;

ALTER TABLE source_record
    ADD CONSTRAINT source_record_normalization_ruleset_format CHECK (
        normalization_ruleset ~ '^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$'
    );
