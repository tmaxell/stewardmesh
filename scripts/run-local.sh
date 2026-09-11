#!/usr/bin/env bash
set -euo pipefail

REPOSITORY_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENVIRONMENT_FILE="${STEWARDMESH_ENV_FILE:-${REPOSITORY_ROOT}/.env}"
if [[ ! -f "${ENVIRONMENT_FILE}" ]]; then
  ENVIRONMENT_FILE="${REPOSITORY_ROOT}/.env.example"
fi

set -a
# shellcheck disable=SC1090
source "${ENVIRONMENT_FILE}"
set +a

cd "${REPOSITORY_ROOT}"
docker compose --env-file "${ENVIRONMENT_FILE}" -f deploy/local/compose.yaml up -d --wait
docker run --rm \
  -v "${REPOSITORY_ROOT}:/workspace" \
  -v stewardmesh-m2:/root/.m2 \
  -w /workspace \
  maven:3.9.16-eclipse-temurin-25 \
  mvn --batch-mode --no-transfer-progress \
    -pl master-data-service/bootstrap-master-service -am package -DskipTests

exec docker run --rm \
  --name stewardmesh-master-service \
  --network stewardmesh_default \
  --env-file "${ENVIRONMENT_FILE}" \
  -e POSTGRES_HOST=postgres \
  -e POSTGRES_PORT=5432 \
  -e STEWARDMESH_S3_ENDPOINT=http://localstack:4566 \
  -e STEWARDMESH_JWK_SET_URI=http://keycloak:8080/realms/stewardmesh/protocol/openid-connect/certs \
  -p 8080:8080 \
  -v "${REPOSITORY_ROOT}:/workspace:ro" \
  -w /workspace \
  maven:3.9.16-eclipse-temurin-25 \
  java -jar master-data-service/bootstrap-master-service/target/bootstrap-master-service-0.1.0-SNAPSHOT.jar
