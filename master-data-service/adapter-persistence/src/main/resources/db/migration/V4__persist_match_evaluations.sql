ALTER TABLE supplier_party_match_index
    ADD COLUMN canonical_legal_name VARCHAR(512);

CREATE TABLE match_evaluation (
    origin_system VARCHAR(128) NOT NULL,
    source_record_id VARCHAR(128) NOT NULL,
    source_version BIGINT NOT NULL,
    ruleset_id VARCHAR(64) NOT NULL,
    evaluated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (origin_system, source_record_id, source_version, ruleset_id),
    FOREIGN KEY (origin_system, source_record_id, source_version)
        REFERENCES source_record (origin_system, source_record_id, source_version),
    CONSTRAINT match_evaluation_ruleset_format CHECK (
        ruleset_id ~ '^[a-z0-9]+(-[a-z0-9]+)*-v[0-9]+$'
    )
);

CREATE TABLE match_decision (
    origin_system VARCHAR(128) NOT NULL,
    source_record_id VARCHAR(128) NOT NULL,
    source_version BIGINT NOT NULL,
    ruleset_id VARCHAR(64) NOT NULL,
    entity_type VARCHAR(8) NOT NULL,
    candidate_id UUID NOT NULL,
    outcome VARCHAR(16) NOT NULL,
    score_basis_points INTEGER NOT NULL,
    hard_conflict BOOLEAN NOT NULL,
    features JSONB NOT NULL,
    PRIMARY KEY (
        origin_system,
        source_record_id,
        source_version,
        ruleset_id,
        entity_type,
        candidate_id
    ),
    FOREIGN KEY (origin_system, source_record_id, source_version, ruleset_id)
        REFERENCES match_evaluation (
            origin_system,
            source_record_id,
            source_version,
            ruleset_id
        ),
    CONSTRAINT match_decision_entity_type_valid CHECK (entity_type IN ('PARTY', 'SITE')),
    CONSTRAINT match_decision_outcome_valid CHECK (outcome IN ('AUTO_LINK', 'REVIEW', 'NO_MATCH')),
    CONSTRAINT match_decision_score_valid CHECK (score_basis_points BETWEEN 0 AND 10000),
    CONSTRAINT match_decision_features_array CHECK (jsonb_typeof(features) = 'array')
);

CREATE INDEX match_decision_source_outcome_idx
    ON match_decision (
        origin_system,
        source_record_id,
        source_version,
        outcome,
        entity_type
    );

CREATE TRIGGER match_evaluation_immutable
    BEFORE UPDATE OR DELETE ON match_evaluation
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();

CREATE TRIGGER match_decision_immutable
    BEFORE UPDATE OR DELETE ON match_decision
    FOR EACH ROW EXECUTE FUNCTION reject_immutable_intake_row();
