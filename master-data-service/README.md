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

Governed onboarding mutations are represented by an immutable `ActionPlan` with a closed vocabulary of typed steps. Every step carries machine-readable reason codes, versioned evidence references and expected aggregate versions; deterministic SHA-256 canonicalization binds the plan ID, version, import, server-owned proposer identity, creation time and all execution-relevant step fields. The `PROPOSE` application boundary performs no master-data mutation and deliberately keeps server-generated identity, time and authenticated actor context outside model-visible arguments.

A second canonical digest covers the proposed import and steps alone. Because it omits identity, time and proposer, a retried proposal recognizes content that still awaits a decision and returns the stored plan instead of sealing a second governed mutation; once that plan is approved, rejected or executed, corrected content seals a fresh plan. `GovernedActionPlan` pairs the sealed plan with an explicit lifecycle — `PROPOSED` to `APPROVED` or `REJECTED`, then `EXECUTING`, then `EXECUTED` or `FAILED` — and refuses every other transition.

Plan persistence separates the mutable governed status from immutable content. JPA owns the status and its optimistic lock; JDBC owns the steps and their evidence, where each action type declares an exact column shape. PostgreSQL independently rejects altered sealed content, an ungoverned status transition and a second undecided plan for the same proposed content. Reads rebuild the plan through its canonical constructor, so storage that drifted from the sealed hash is reported rather than returned.

The `SIMULATE` boundary explains a sealed plan without touching master data. It refuses to answer unless the caller binds the exact plan version and hash, reads only the entities the plan names, and applies the steps in sealed order over a projected overlay, so a step may depend on a party, site or authorization an earlier step of the same plan creates. Verdicts carry stable precondition codes and the step sequence only; identifiers stay in the plan and supplier values never reach a simulation result. Simulation covers preconditions — existence, expected aggregate versions, business-unit roles and colliding authorizations. Attribute-level golden-record preview belongs with the execution write model and is not claimed here. Approval and execution remain separate later capabilities that must bind this same plan version and hash.

The `APPROVE` boundary records one immutable human decision and advances the plan in the same PostgreSQL transaction. The application derives subject and authorization from server context, while the domain policy requires an exact plan version/hash, an authorized principal distinct from the proposer, a bounded reason and an idempotency key. PostgreSQL independently prevents a plan from entering `APPROVED` or `REJECTED` without its matching decision record and makes that record undeletable and unchangeable. Replaying the same subject/key/decision returns the original result; reusing it for different content is a conflict.

The `EXECUTE` boundary re-simulates an exactly version/hash-bound approved plan before applying its sealed steps. Source-bearing steps require one versioned match-evaluation evidence reference and reuse the existing projection use case; site creation verifies the exact site materialized by that projection, and assignment reuses the organization policy. Master mutations, the immutable execution receipt, one redacted audit record, one broker-neutral outbox event per effect and the final `EXECUTED` transition commit atomically. A server-derived subject plus idempotency key returns the same receipt on retry and conflicts if reused for different content; no broker or model call occurs inside the transaction.

`IdentityResolutionEndToEndIT` is the Phase 2 acceptance proof. Against disposable PostgreSQL it verifies five supplier outcomes, idempotent reruns, stewardship routing, golden provenance, secured REST reads, metrics and the latency smoke threshold without introducing production-only test hooks.
