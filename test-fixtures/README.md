# Test fixtures

Synthetic XLSX packages and canonical integration events. No real company, supplier, personal or banking data is allowed here.

The `intake/` directory contains fixtures for the versioned supplier workbook contract. Every fixture is intentionally synthetic and is verified by the XLSX ingestion adapter tests.

The Phase 2 identity-resolution acceptance scenarios are assembled in `IdentityResolutionEndToEndIT` so each run owns its source identities, match-index candidates and disposable PostgreSQL state. The five named scenarios cover new, exact, fuzzy, identifier-conflict and existing-party/new-site outcomes; no external or production data is required.
