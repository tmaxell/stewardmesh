# StewardMesh

**Agentic Master Data Control Plane for Retail**

StewardMesh is a greenfield Java/Spring platform for governed supplier onboarding and master-data stewardship. It combines an explainable Partner & Location MDM core, headless REST/MCP capabilities, a reference agent, and reliable integration with an enterprise reference-data distribution contour.

The repository contains the executable foundation for the first supplier-intake vertical slice. For contribution and branch rules see [CONTRIBUTING.md](CONTRIBUTING.md).

## Repository map

```text
.github/workflows/   CI workflows
contracts/           Versioned REST, MCP and event contracts
deploy/local/        Local PostgreSQL and LocalStack dependencies
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
- PostgreSQL with immutable JSONB source assertions and explicit indexed matching columns.
- Inbox/outbox integration with broker-neutral application ports.
- SQS adapter in the target stack; RabbitMQ compatibility for an enterprise NSI distribution contour.
- Greenfield implementation rather than a fork of an existing MDM product.

## Delivered milestones

Phase 1 delivers the first intake vertical slice on the Maven foundation:

`XLSX upload -> S3 artifact -> ImportJob -> SourceRecord -> validation report`.

The proof covers a valid workbook, deterministic mixed-row evidence, idempotent replay, duplicate source-identity recovery, hostile input rejection, and infrastructure failure boundaries.

```text
POST workbook -> immutable S3 object -> artifact/job transaction
                                      -> bounded XLSX parsing
                                      -> source rows/report transaction
GET status/report <------------------- validated or failed job
```

Phase 2 delivers deterministic supplier source normalization, bounded identity resolution, stewardship routing and explainable golden records. Original assertions remain unchanged; separately stored canonical values carry the exact normalization ruleset identifier so matching and golden-record projections can be reproduced. PostgreSQL blocking produces bounded party and site candidates, then the versioned `supplier-identity-v1` ruleset calculates basis-point scores from identifier equality and token similarity. Authoritative identifier conflicts prevent automatic links.

Every candidate decision is stored immutably with its ruleset, outcome, score, hard-conflict flag and complete feature evidence. Evidence contains stable feature codes and contributions, never raw supplier values. Repeating the same source-version/ruleset evaluation is idempotent.

Validated imports can be handed to a resumable matching use case. It rejects stale source versions before processing, reuses already persisted evaluations after a retry, and finishes as `MATCHED` or `REVIEW_REQUIRED`. Review/conflict outcomes create one immutable, idempotent stewardship case per source version and scoring ruleset. Read-only OAuth-protected endpoints expose bounded status, candidates, explanations and current golden records without bypassing the application layer.

## Build and run

Java 25 is required. Maven is supplied by the repository wrapper.

```bash
./scripts/verify-phase-2.sh
docker compose --env-file .env -f deploy/local/compose.yaml up -d --wait
java -jar master-data-service/bootstrap-master-service/target/bootstrap-master-service-0.1.0-SNAPSHOT.jar
```

At startup, the service connects to PostgreSQL, applies the Flyway intake schema, validates its JPA mappings and configures immutable intake storage plus bounded supplier-workbook parsing. The supplier intake API is published under `/api/v1/supplier-imports`; its OpenAPI document is available at `/v3/api-docs`.

The API is an OAuth2 resource server. Configure `STEWARDMESH_JWK_SET_URI` for the JWT issuer. Upload requires `supplier-import.write`; status and report reads require `supplier-import.read`. The default local URI is only a development boundary and does not embed credentials or keys.

The disposable E2E proof supplies synthetic authenticated principals without requiring a local identity provider. Running the service itself requires a reachable JWK set URI and appropriately scoped JWTs.

With a scoped development token, the synthetic fixture can be submitted as follows:

```bash
curl --fail-with-body \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Idempotency-Key: synthetic-demo-1" \
  -F "sourceSystem=SYNTHETIC_DEMO" \
  -F "workbook=@test-fixtures/intake/supplier-workbook-v1-valid.xlsx;type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
  http://localhost:8080/api/v1/supplier-imports
```

Use the returned `statusUrl` and `reportUrl` with a token carrying `supplier-import.read`. Identity-resolution and golden-record reads require `identity-resolution.read`. The versioned response and problem schemas live in the [supplier-import contract](contracts/openapi/supplier-imports-v1.yaml) and [identity-resolution contract](contracts/openapi/identity-resolution-v1.yaml).

## Phase 2 reproducible demo

The Phase 2 E2E proof starts a disposable PostgreSQL instance and exercises the real application, persistence and REST boundaries with synthetic data. It covers a new supplier, an exact duplicate, a fuzzy review, a conflicting authoritative identifier and an existing party with a new site. The proof also checks deterministic reruns, stewardship-case idempotency, golden-record provenance, bounded read APIs, metrics and a ten-second latency smoke threshold.

```bash
./mvnw --batch-mode --no-transfer-progress \
  -pl master-data-service/bootstrap-master-service -am verify \
  -Dit.test=IdentityResolutionEndToEndIT \
  -Dfailsafe.failIfNoSpecifiedTests=false
```

Run `./scripts/verify-phase-2.sh` for the complete repository gate, including the full test suite, aggregate coverage, Compose validation and Git hygiene checks. Java 25 and Docker are required; all demo identities are generated synthetic fixtures.

Actuator health, metrics and Prometheus output are exposed under `/actuator`. Metrics cover intake outcomes, rows, artifact bytes, stage duration/failures, validation codes, match-scoring duration/failures, bounded candidate counts and decision outcomes. Matching metric labels use only bounded entity/outcome/conflict dimensions. Console logs use structured JSON and never include workbook rows or supplier identifiers.

## Local dependencies

```bash
cp .env.example .env
docker compose --env-file .env -f deploy/local/compose.yaml up -d --wait
```

This starts PostgreSQL plus LocalStack with the development S3 bucket and SQS queues. See [the local environment guide](deploy/local/README.md).
