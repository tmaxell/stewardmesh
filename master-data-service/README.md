# Master Data Service

Authoritative modular MDM service. Planned modules:

- `master-domain` — framework-free aggregates, policies and invariants.
- `master-application` — use cases and inbound/outbound ports.
- `adapter-rest` — system REST API and OpenAPI.
- `adapter-mcp` — headless MDM, data-quality and stewardship tools.
- `adapter-persistence` — JPA/JDBC and Flyway.
- `adapter-ingestion-xlsx` — bounded Apache POI intake.
- `adapter-messaging` — inbox/outbox and broker adapters.
- `bootstrap-master-service` — Spring Boot composition root.

Modules will be activated by vertical slices; domain code must not depend on adapters.
