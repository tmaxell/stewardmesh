# Local environment

The local runtime uses Keycloak for OAuth2 client credentials, PostgreSQL for mastered data and LocalStack for S3 and SQS.

From the repository root:

```bash
./scripts/run-local.sh
```

The script uses `.env` when present and otherwise the safe local placeholders in `.env.example`. It waits for all dependencies, then builds and starts the Java 25 service in a pinned Maven container with the same environment. Only Docker is required locally. In another terminal, run `./scripts/smoke-local.sh` to acquire a scoped token and verify an upload plus its idempotent replay.

Default endpoints:

- PostgreSQL: `localhost:5432`, database/user `stewardmesh`;
- LocalStack: `http://localhost:4566`;
- Keycloak: `http://localhost:8081`, realm `stewardmesh`;
- S3 bucket: `stewardmesh-intake`;
- SQS queues: `stewardmesh-source-events` and `stewardmesh-master-events`.

The checked-in credentials and OAuth client secret are local-only placeholders. Override them in the ignored `.env` file and never reuse them in a shared environment.

Stop containers without removing data:

```bash
docker compose --env-file .env -f deploy/local/compose.yaml down
```

Removing named volumes destroys local development data and must be an explicit developer action.
