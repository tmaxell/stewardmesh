# Architecture and stack decisions

Read this reference for module boundaries, framework usage, persistence, integrations, or testing infrastructure.

## Target shape

Use one Maven reactor with two runnable applications:

- `master-data-service`: modular monolith containing the MDM domain, use cases, REST, MCP server, persistence, jobs, and event publication.
- `steward-agent`: small reference agent that connects only through published MCP contracts.

Keep domain modules free of Spring, JPA annotations, transport DTOs, AWS types, and model-provider types. Application services coordinate domain objects through ports. Adapters own frameworks and serialization.

Suggested master-service modules:

- `master-domain`
- `master-application`
- `adapter-rest`
- `adapter-mcp`
- `adapter-persistence`
- `adapter-ingestion-xlsx`
- `adapter-messaging`
- `bootstrap-master-service`

Do not create all modules before a vertical slice needs them. Preserve logical boundaries even if early implementation uses fewer physical Maven modules.

## Stack responsibilities

- Java 25: language/runtime baseline; use records for immutable boundary values and sealed types when the closed hierarchy is real.
- Spring Boot 4.0.5 and Spring MVC: application runtime, REST, and Streamable HTTP MCP transport.
- Spring Security OAuth2 Resource Server: JWT validation, audience/resource checks, scope-to-authority mapping, and method authorization.
- SpringDoc: REST documentation only; MCP schemas come from MCP tool definitions.
- JPA: aggregate-oriented transactional writes and ordinary queries.
- JDBC: batch ingestion, candidate generation, scoring queries, outbox claiming, and recursive/analytic SQL where ORM obscures intent.
- Apache POI: streaming XLSX intake and validation reports. Bound workbook, row, column, shared-string, and cell sizes.
- MapStruct: explicit DTO/domain and persistence/domain mapping; do not hide business decisions in mappers.
- AWS SDK S3: immutable intake artifacts and generated reports.
- AWS SDK SQS with Apache HTTP client: primary integration-event adapter for the target stack.
- Spring AMQP/RabbitMQ: optional compatibility adapter when the enterprise NSI contour requires it; do not leak broker types into application or domain modules.
- Quartz: scheduled reconciliation, rematch, data-quality, and recovery jobs; jobs must be idempotent and cluster-safe if clustering is enabled.
- Actuator, Micrometer, Prometheus: health, latency, throughput, outcome, and queue/backlog metrics without sensitive labels.
- Log4j2: structured logs with trace, onboarding, import, proposal, and entity identifiers; never log secrets or full financial identifiers.
- Lombok: limited convenience in infrastructure code. Prefer explicit domain code and Java records.

Use PostgreSQL and Flyway. Use Testcontainers for PostgreSQL, LocalStack, and Keycloak-oriented integration tests when the boundary requires it. Use WireMock for external registry adapters.

## Transaction and messaging rules

- Model inbound and outbound messaging through broker-neutral application ports. The domain must not know queues, exchanges, routing keys, receipt handles, or delivery tags.
- Inbound at-least-once delivery uses a transactional inbox. Record the stable business `eventId` and business effect before acknowledging delivery.
- A business mutation and its outbox record commit in the same database transaction.
- Publication to SQS or RabbitMQ occurs asynchronously from the outbox.
- Consumers and scheduled jobs are idempotent.
- Distinguish `originSystem`, `producer`, and `transportSystem`; preserve correlation, causation, schema version, subject version, and integration checkpoints.
- Prevent relay loops using business event identity, inbox deduplication, source/entity version, and meaningful-payload fingerprint. Broker message IDs alone are insufficient.
- Replay must pass through the same validation and inbox path. Data failures go to quarantine; exhausted transient failures go to DLQ and reconciliation.
- Optimistic locking protects steward decisions and action-plan execution.
- Do not hold a database transaction open across LLM, MCP, S3, SQS, or external registry calls.

## Contract design

- REST and MCP call the same application use cases.
- Transport DTOs are versionable and do not expose persistence entities.
- Mutation commands include expected version and idempotency key.
- Return stable error codes and structured evidence; avoid making agents parse prose to determine state.
- Tool results should be bounded and paginated. Large reports belong in S3-backed resources, not huge tool responses.

## Verification

- Domain unit tests cover invariants and state transitions.
- Persistence integration tests prove constraints, locking, JDBC queries, and migrations against PostgreSQL.
- REST tests verify validation, status mapping, and OpenAPI compatibility.
- MCP contract tests verify schemas, authentication, authorization, bounded output, and error shapes.
- LocalStack tests cover S3/SQS integration and inbox/outbox behavior.
- Messaging contract tests cover duplicate delivery, relay loops, out-of-order versions, quarantine, replay, and adapter-independent canonical envelopes.
- Architecture tests can enforce adapter/domain dependency direction once the module structure exists.
