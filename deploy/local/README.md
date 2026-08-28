# Local environment

The first implementation slice uses PostgreSQL plus LocalStack for S3 and SQS. Keycloak, RabbitMQ compatibility and Prometheus will be added with the vertical slices that exercise them.

From the repository root:

```bash
cp .env.example .env
docker compose --env-file .env -f deploy/local/compose.yaml up -d
docker compose --env-file .env -f deploy/local/compose.yaml ps
```

Default endpoints:

- PostgreSQL: `localhost:5432`, database/user `stewardmesh`;
- LocalStack: `http://localhost:4566`;
- S3 bucket: `stewardmesh-intake`;
- SQS queues: `stewardmesh-source-events` and `stewardmesh-master-events`.

The checked-in credentials are local-only placeholders. Override them in the ignored `.env` file.

Stop containers without removing data:

```bash
docker compose --env-file .env -f deploy/local/compose.yaml down
```

Removing named volumes destroys local development data and must be an explicit developer action.
