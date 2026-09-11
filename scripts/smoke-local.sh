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

TOKEN_ENDPOINT="http://localhost:${KEYCLOAK_PORT}/realms/stewardmesh/protocol/openid-connect/token"
TOKEN_RESPONSE="$(curl --fail-with-body --silent --show-error \
  --data-urlencode "client_id=${STEWARDMESH_OAUTH_CLIENT_ID}" \
  --data-urlencode "client_secret=${STEWARDMESH_OAUTH_CLIENT_SECRET}" \
  --data-urlencode "grant_type=client_credentials" \
  "${TOKEN_ENDPOINT}")"
TOKEN="$(printf '%s' "${TOKEN_RESPONSE}" | sed -n 's/.*"access_token":"\([^"]*\)".*/\1/p')"
if [[ -z "${TOKEN}" ]]; then
  echo "Keycloak did not return an access token" >&2
  exit 1
fi

IDEMPOTENCY_KEY="local-smoke-$(date +%s)"
upload() {
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Idempotency-Key: ${IDEMPOTENCY_KEY}" \
    -F "sourceSystem=SYNTHETIC_LOCAL_SMOKE" \
    -F "workbook=@${REPOSITORY_ROOT}/test-fixtures/intake/supplier-workbook-v1-valid.xlsx;type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
    http://localhost:8080/api/v1/supplier-imports
}

FIRST_RESPONSE="$(upload)"
SECOND_RESPONSE="$(upload)"
printf '%s' "${FIRST_RESPONSE}" | grep -q '"replayed":false'
printf '%s' "${SECOND_RESPONSE}" | grep -q '"replayed":true'

echo "Local smoke passed: initial upload and idempotent replay succeeded."
