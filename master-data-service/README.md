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
