ALTER TABLE source_association
    ADD COLUMN party_association_kind VARCHAR(16) NOT NULL DEFAULT 'AUTO_LINK',
    ADD COLUMN site_association_kind VARCHAR(16);

ALTER TABLE source_association
    ADD CONSTRAINT source_association_party_kind_valid
        CHECK (party_association_kind IN ('AUTO_LINK', 'NEW_ENTITY')),
    ADD CONSTRAINT source_association_site_kind_valid
        CHECK (site_association_kind IS NULL OR site_association_kind IN ('AUTO_LINK', 'NEW_ENTITY')),
    ADD CONSTRAINT source_association_site_evidence_shape CHECK (
        (site_id IS NULL AND site_association_kind IS NULL)
        OR (site_id IS NOT NULL AND site_association_kind IS NOT NULL)
    );
