CREATE TABLE supplier_party_match_index (
    party_id UUID PRIMARY KEY,
    canonical_inn VARCHAR(12) NOT NULL,
    canonical_ogrn VARCHAR(15),
    CONSTRAINT supplier_party_match_inn_format CHECK (
        canonical_inn ~ '^([0-9]{10}|[0-9]{12})$'
    ),
    CONSTRAINT supplier_party_match_ogrn_format CHECK (
        canonical_ogrn IS NULL OR canonical_ogrn ~ '^([0-9]{13}|[0-9]{15})$'
    )
);

CREATE INDEX supplier_party_match_inn_idx
    ON supplier_party_match_index (canonical_inn, party_id);

CREATE INDEX supplier_party_match_ogrn_idx
    ON supplier_party_match_index (canonical_ogrn, party_id)
    WHERE canonical_ogrn IS NOT NULL;

CREATE TABLE supplier_site_match_index (
    site_id UUID PRIMARY KEY,
    party_id UUID NOT NULL REFERENCES supplier_party_match_index (party_id),
    canonical_inn VARCHAR(12) NOT NULL,
    canonical_kpp VARCHAR(9),
    canonical_site_code VARCHAR(128),
    canonical_country_code VARCHAR(2) NOT NULL,
    canonical_postal_code VARCHAR(32),
    canonical_region VARCHAR(512),
    canonical_city VARCHAR(512) NOT NULL,
    canonical_address_line VARCHAR(1024) NOT NULL,
    canonical_address_hash VARCHAR(32) GENERATED ALWAYS AS (
        md5(canonical_country_code || chr(31) || canonical_city || chr(31) || canonical_address_line)
    ) STORED,
    CONSTRAINT supplier_site_match_inn_format CHECK (
        canonical_inn ~ '^([0-9]{10}|[0-9]{12})$'
    ),
    CONSTRAINT supplier_site_match_kpp_format CHECK (
        canonical_kpp IS NULL OR canonical_kpp ~ '^[0-9]{9}$'
    ),
    CONSTRAINT supplier_site_match_country_format CHECK (
        canonical_country_code ~ '^[A-Z]{2}$'
    ),
    CONSTRAINT supplier_site_match_city_present CHECK (btrim(canonical_city) <> ''),
    CONSTRAINT supplier_site_match_address_present CHECK (btrim(canonical_address_line) <> '')
);

CREATE INDEX supplier_site_match_inn_kpp_idx
    ON supplier_site_match_index (canonical_inn, canonical_kpp, site_id)
    WHERE canonical_kpp IS NOT NULL;

CREATE INDEX supplier_site_match_code_idx
    ON supplier_site_match_index (canonical_site_code, site_id)
    WHERE canonical_site_code IS NOT NULL;

CREATE INDEX supplier_site_match_address_idx
    ON supplier_site_match_index (
        canonical_country_code,
        canonical_address_hash,
        site_id
    );
