# StewardMesh

**Agentic Master Data Control Plane for Retail**

StewardMesh is a greenfield Java/Spring platform for governed supplier onboarding and master-data stewardship. It combines an explainable Partner & Location MDM core, headless REST/MCP capabilities, a reference agent, and reliable integration with an enterprise reference-data distribution contour.

The repository is in the architecture and MVP-definition stage. For contribution and branch rules see [CONTRIBUTING.md](CONTRIBUTING.md).

## Repository map

```text
.agents/             Repository-specific Codex skill
.github/workflows/   CI workflows
contracts/           Versioned REST, MCP and event contracts
deploy/local/        Local PostgreSQL, Keycloak, broker and observability setup
evals/               Frozen agent scenarios and graders
master-data-service/ Modular MDM service
steward-agent/       Reference MCP-based agent
test-fixtures/       Synthetic workbooks and integration events
scripts/             Small reproducible developer utilities
```

## Product boundary

The MVP masters supplier parties and supplier sites and assigns them to procurement/client business units. It does not master products, customers, employees, contracts, payments, or the complete internal organization hierarchy.

## Current decisions

- Java 25 and Spring Boot 4.0.5.
- Modular monolith for the authoritative MDM service.
- Separate thin reference agent consuming only MCP contracts.
- PostgreSQL, immutable source records, projected golden records.
- Inbox/outbox integration with broker-neutral application ports.
- SQS adapter in the target stack; RabbitMQ compatibility for an enterprise NSI distribution contour.
- Greenfield implementation rather than a fork of an existing MDM product.

## Next milestone

Create the Maven reactor and deliver the first vertical slice:

`XLSX upload -> S3 artifact -> ImportJob -> SourceRecord -> validation report`.

## Local dependencies

```bash
cp .env.example .env
docker compose --env-file .env -f deploy/local/compose.yaml up -d --wait
```

This starts PostgreSQL plus LocalStack with the development S3 bucket and SQS queues. See [the local environment guide](deploy/local/README.md).
