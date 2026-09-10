# Master Data Service

Authoritative modular MDM service. Active foundation modules:

- `master-domain` — framework-free aggregates, policies and invariants.
- `master-application` — use cases and inbound/outbound ports.
- `adapter-rest` — system REST API and OpenAPI.
- `adapter-persistence` — JPA/JDBC and Flyway.
- `adapter-ingestion-xlsx` — bounded Apache POI intake.
- `bootstrap-master-service` — Spring Boot composition root.

`adapter-mcp`, `adapter-messaging` and `steward-agent` remain inactive until their first working vertical slices. Dependencies point inward: domain <- application <- adapters <- bootstrap.

The identity-resolution domain distinguishes party and supplier-site candidates, retains bounded feature-level evidence, and applies explicit versioned thresholds. Authoritative identifier conflicts can never produce an automatic link.

Candidate blocking is exposed through a bounded application use case and persistence-neutral output ports. The application layer deduplicates evidence, orders results deterministically, and reports truncation; query strategy and storage remain adapter concerns.

PostgreSQL maintains compact party and supplier-site match indexes as derived lookup projections rather than master aggregates. JDBC performs bounded INN/OGRN, INN+KPP, site-code, and normalized-address blocking; the application remains responsible for deduplication and truncation semantics.

The golden-record domain keeps `SupplierParty`, `SupplierAddress`, and `SupplierSite` as separate versioned projections. Initial survivorship selects canonical attributes by explicit source priority, recency, completeness, and a stable tie-break; every selected value retains its source record, association, rule, ruleset, and decision time. Only conflict-free automatic match decisions can create source associations, and unlinking retains the original evidence for later explanation or reversal.

Golden projection persistence separates mutable aggregate metadata from immutable history. JPA owns current projection metadata and its optimistic lock, while JDBC atomically stores source associations, version snapshots, association membership, and attribute provenance. Reads reconstruct only the current domain projection; prior versions remain queryable evidence in PostgreSQL and a failed batch rolls back metadata and lineage together.
