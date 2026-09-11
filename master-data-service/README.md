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

The read API keeps the same boundaries: REST controllers call bounded application queries, while JDBC adapters reconstruct persisted match evidence and golden projections. OAuth scope `identity-resolution.read` protects resolution status, candidate, explanation and golden-record endpoints; the versioned wire contract is maintained in `contracts/openapi/identity-resolution-v1.yaml`.

Business units are imported versioned reference context, not a second mastered organization hierarchy. A supplier-site assignment separately authorizes a client business unit to use an existing mastered supplier site for explicit procure-to-pay purposes and an inclusive business-date interval. Domain and PostgreSQL guards reject overlapping site/client/purpose authorizations; current records use optimistic locking and immutable history retains every accepted reference or assignment version. These application capabilities are transport-neutral so later governed plans, MCP tools and source-event consumers can share them.

`IdentityResolutionEndToEndIT` is the Phase 2 acceptance proof. Against disposable PostgreSQL it verifies five supplier outcomes, idempotent reruns, stewardship routing, golden provenance, secured REST reads, metrics and the latency smoke threshold without introducing production-only test hooks.
