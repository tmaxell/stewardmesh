#!/usr/bin/env bash
#
# Reproducible end-to-end demonstration against the local stack.
#
# Start the dependencies and the service with ./scripts/run-local.sh first, then run this in a
# second terminal. Every identity, supplier and reference event below is synthetic.
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

SERVICE="${STEWARDMESH_SERVICE_URL:-http://localhost:8080}"
REALM="http://localhost:${KEYCLOAK_PORT}/realms/stewardmesh/protocol/openid-connect/token"
RUN="$(date +%s)$$"
WORKDIR="$(mktemp -d)"
trap 'rm -rf "${WORKDIR}"' EXIT

command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }
command -v docker >/dev/null || { echo "docker is required for the queue steps" >&2; exit 1; }

step() { printf '\n\033[1m%s\033[0m\n' "$*"; }
detail() { printf '    %s\n' "$*"; }

# One authenticated Streamable HTTP MCP call. Each role opens its own session, which is what
# separate principals do in practice.
cat > "${WORKDIR}/mcp.py" <<'PYTHON'
import json, sys, urllib.request

base, token, tool = sys.argv[1], sys.argv[2], sys.argv[3]
protocol = "2025-06-18"
request = json.load(sys.stdin)


def post(body, session=None):
    headers = {
        "Authorization": "Bearer " + token,
        "Content-Type": "application/json",
        "Accept": "application/json, text/event-stream",
        "MCP-Protocol-Version": protocol,
    }
    if session:
        headers["Mcp-Session-Id"] = session
    call = urllib.request.Request(base + "/mcp", json.dumps(body).encode(), headers)
    with urllib.request.urlopen(call) as response:
        return response.headers.get("Mcp-Session-Id"), response.read().decode()


session, _ = post({
    "jsonrpc": "2.0", "id": 1, "method": "initialize",
    "params": {"protocolVersion": protocol, "capabilities": {},
               "clientInfo": {"name": "stewardmesh-demo", "version": "1.0"}}})
post({"jsonrpc": "2.0", "method": "notifications/initialized"}, session)
arguments = ({"request": request} if tool in {
    "create_onboarding_proposal", "simulate_onboarding_plan", "approve_action_plan",
    "reject_action_plan", "execute_approved_plan"} else request)
_, raw = post({"jsonrpc": "2.0", "id": 2, "method": "tools/call",
               "params": {"name": tool, "arguments": arguments}}, session)

payload = raw.strip()
if not payload.startswith("{"):
    payload = next(line[5:].strip() for line in payload.splitlines() if line.startswith("data:"))
envelope = json.loads(payload)
result = envelope.get("result") or {}
if "error" in envelope or result.get("isError"):
    sys.stderr.write(json.dumps(envelope.get("error") or result)[:500] + "\n")
    sys.exit(1)
print(result["content"][0]["text"])
PYTHON

mcp() { python3 "${WORKDIR}/mcp.py" "${SERVICE}" "$@"; }
field() { python3 -c "import sys,json;print(json.load(sys.stdin)$1)"; }

token_for() {
  curl --fail-with-body --silent --show-error \
    --data-urlencode "client_id=$1" \
    --data-urlencode "client_secret=$1-secret" \
    --data-urlencode "grant_type=client_credentials" \
    "${REALM}" | field "['access_token']"
}

curl --fail-with-body --silent --show-error "${SERVICE}/actuator/health" >/dev/null || {
  echo "The service is not answering at ${SERVICE}. Start ./scripts/run-local.sh first." >&2
  exit 1
}

step "1. Least-privilege service accounts"
IMPORTER="$(token_for stewardmesh-local)"
AGENT="$(token_for stewardmesh-agent)"
STEWARD="$(token_for stewardmesh-steward)"
EXECUTOR="$(token_for stewardmesh-executor)"
for role in stewardmesh-agent stewardmesh-steward stewardmesh-executor; do
  detail "$(printf '%-22s %s' "${role}" "$(token_for "${role}" | python3 -c "
import sys, base64, json
claims = sys.stdin.read().split('.')[1]
claims += '=' * (-len(claims) % 4)
print(json.loads(base64.urlsafe_b64decode(claims))['scope'])")")"
done

step "2. A reference business unit arrives from the distribution contour"
# A distribution contour owns one identifier per business unit code, so the id is derived from the
# code rather than invented. The code carries the run so the demo is reproducible against a database
# that already holds earlier runs, instead of only against a pristine one.
BUSINESS_UNIT_CODE="DEMO-CLIENT-BU-${RUN}"
BUSINESS_UNIT="$(python3 -c "import sys,uuid;print(uuid.uuid5(uuid.NAMESPACE_URL,'stewardmesh-demo/'+sys.argv[1]))" "${BUSINESS_UNIT_CODE}")"
python3 - "${BUSINESS_UNIT}" "${BUSINESS_UNIT_CODE}" > "${WORKDIR}/business-unit.json" <<'PYTHON'
import datetime, json, sys, uuid

occurred = datetime.datetime(2026, 1, 1, tzinfo=datetime.timezone.utc)
stamp = lambda moment: moment.isoformat().replace("+00:00", "Z")
print(json.dumps({
    "eventId": str(uuid.uuid4()), "eventType": "BusinessUnitReferenceChanged",
    "schemaVersion": 1, "subjectType": "BUSINESS_UNIT", "subjectId": sys.argv[1],
    "entityVersion": 1, "originSystem": "SYNTHETIC_NSI", "producer": "synthetic-distributor",
    "transportSystem": "sqs", "occurredAt": stamp(occurred),
    "publishedAt": stamp(occurred + datetime.timedelta(seconds=1)),
    "correlationId": str(uuid.uuid4()), "causationId": None,
    "traceId": "demo-reference-trace", "dataClassification": "INTERNAL",
    "payload": {"code": sys.argv[2], "displayName": "Synthetic Demo Client",
                "roles": "CLIENT,PROCUREMENT", "validFrom": "2026-01-01"}}))
PYTHON
docker cp "${WORKDIR}/business-unit.json" stewardmesh-localstack-1:/tmp/demo-business-unit.json >/dev/null
docker exec stewardmesh-localstack-1 awslocal sqs send-message \
  --queue-url "http://localhost:4566/000000000000/${STEWARDMESH_SOURCE_EVENTS_QUEUE}" \
  --message-body file:///tmp/demo-business-unit.json >/dev/null
detail "published BusinessUnitReferenceChanged for ${BUSINESS_UNIT}"
detail "the inbox consumes it once; step 6 proves the service saw it, without reading the database"

step "3. A synthetic supplier workbook enters through the public API"
UPLOAD="$(curl --fail-with-body --silent --show-error \
  -H "Authorization: Bearer ${IMPORTER}" \
  -H "Idempotency-Key: demo-${RUN}" \
  -F "sourceSystem=DEMO_${RUN}" \
  -F "workbook=@${REPOSITORY_ROOT}/test-fixtures/intake/supplier-workbook-v1-valid.xlsx;type=application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" \
  "${SERVICE}/api/v1/supplier-imports")"
IMPORT_ID="$(printf '%s' "${UPLOAD}" | field "['importId']")"
detail "import ${IMPORT_ID} reached $(printf '%s' "${UPLOAD}" | field "['status']")"

step "4. The demo discovers the mastered site through the public API"
SITE="$(curl --fail-with-body --silent --show-error -H "Authorization: Bearer ${IMPORTER}" \
  "${SERVICE}/api/v1/identity-resolution/sources/DEMO_${RUN}/SYN-0002/versions/1/candidates?rulesetId=supplier-identity-v1&entityType=SITE" \
  | field "['candidates'][0]['candidateId']")"
SITE_VERSION="$(curl --fail-with-body --silent --show-error -H "Authorization: Bearer ${IMPORTER}" \
  "${SERVICE}/api/v1/golden-records/SITE/${SITE}" | field "['version']")"
detail "site ${SITE} is at golden version ${SITE_VERSION}"

ASSIGNMENT="$(python3 -c 'import uuid;print(uuid.uuid4())')"
python3 - "${IMPORT_ID}" "${ASSIGNMENT}" "${SITE}" "${SITE_VERSION}" "${BUSINESS_UNIT}" \
  "${BUSINESS_UNIT_CODE}" > "${WORKDIR}/proposal.json" <<'PYTHON'
import json, sys

import_id, assignment, site, site_version, business_unit = sys.argv[1:6]
print(json.dumps({"importId": import_id, "steps": [{
    "sequence": 1, "type": "ASSIGN_SUPPLIER_SITE", "assignmentId": assignment,
    "siteId": site, "expectedSiteVersion": int(site_version),
    "clientBusinessUnitId": business_unit, "purposes": ["PURCHASING"],
    "validFrom": "2026-01-01", "reasonCode": "AUTHORIZE_CLIENT_BU",
    "evidence": [{"type": "BUSINESS_UNIT_REFERENCE", "reference": sys.argv[6], "version": 1}]}]}))
PYTHON
if [[ -n "${GROQ_API_KEY:-}" ]]; then
  step "5. The Groq-backed reference supervisor investigates and proposes"
  OBJECTIVE="Inspect source DEMO_${RUN}/SYN-0002/v1 with ruleset supplier-identity-v1. Propose ASSIGN_SUPPLIER_SITE assignmentId=${ASSIGNMENT} siteId=${SITE} expectedSiteVersion=${SITE_VERSION} clientBusinessUnitId=${BUSINESS_UNIT} purposes=PURCHASING validFrom=2026-01-01 reasonCode=AUTHORIZE_CLIENT_BU evidence=BUSINESS_UNIT_REFERENCE:${BUSINESS_UNIT_CODE}:v1."
  docker run --rm \
    -v "${REPOSITORY_ROOT}:/workspace" -v stewardmesh-m2:/root/.m2 -w /workspace \
    maven:3.9.16-eclipse-temurin-25 \
    mvn --batch-mode --no-transfer-progress -pl steward-agent -am package -DskipTests >/dev/null
  export STEWARDMESH_AGENT_TOKEN="${AGENT}"
  export STEWARDMESH_IMPORT_ID="${IMPORT_ID}"
  export STEWARDMESH_AGENT_OBJECTIVE="${OBJECTIVE}"
  AGENT_RESULT="$(docker run --rm --network stewardmesh_default \
    -e GROQ_API_KEY \
    -e "GROQ_MODEL=${GROQ_MODEL:-openai/gpt-oss-20b}" \
    -e "GROQ_BASE_URL=${GROQ_BASE_URL:-https://api.groq.com/openai/v1/}" \
    -e STEWARDMESH_AGENT_TOKEN \
    -e STEWARDMESH_IMPORT_ID \
    -e STEWARDMESH_AGENT_OBJECTIVE \
    -e STEWARDMESH_MCP_URL=http://stewardmesh-master-service:8080/mcp \
    -v "${REPOSITORY_ROOT}:/workspace:ro" -w /workspace \
    maven:3.9.16-eclipse-temurin-25 \
    java -jar steward-agent/target/steward-agent-0.1.0-SNAPSHOT.jar)"
  PLAN="$(printf '%s' "${AGENT_RESULT}" | field "['planId']")"
  PLAN_VERSION="$(printf '%s' "${AGENT_RESULT}" | field "['planVersion']")"
  HASH="$(printf '%s' "${AGENT_RESULT}" | field "['planHash']")"
  detail "reference supervisor used $(printf '%s' "${AGENT_RESULT}" | field "['model']")"
  detail "tool calls $(printf '%s' "${AGENT_RESULT}" | field "['toolCalls']")"
else
  step "5. Deterministic MCP fallback proposes the governed change"
  detail "GROQ_API_KEY is not set; exporting it enables the real reference-supervisor path"
  PROPOSAL="$(mcp "${AGENT}" create_onboarding_proposal < "${WORKDIR}/proposal.json")"
  PLAN="$(printf '%s' "${PROPOSAL}" | field "['planId']")"
  PLAN_VERSION="$(printf '%s' "${PROPOSAL}" | field "['version']")"
  HASH="$(printf '%s' "${PROPOSAL}" | field "['hash']")"
fi
detail "plan ${PLAN} sealed at version ${PLAN_VERSION}"
printf '{"planId":"%s"}' "${PLAN}" > "${WORKDIR}/plan-read.json"
PLAN_READ="$(mcp "${AGENT}" get_action_plan < "${WORKDIR}/plan-read.json")"
printf '%s' "${PLAN_READ}" | python3 -c '
import json, sys
plan = json.load(sys.stdin)
assignment, site, business_unit, version, digest = sys.argv[1:]
steps = plan["steps"]
assert plan["status"] == "PROPOSED" and str(plan["version"]) == version and plan["hash"] == digest
assert len(steps) == 1 and steps[0]["type"] == "ASSIGN_SUPPLIER_SITE"
targets = steps[0]["targets"]
assert targets["assignmentId"] == assignment
assert targets["siteId"] == site
assert targets["clientBusinessUnitId"] == business_unit
' "${ASSIGNMENT}" "${SITE}" "${BUSINESS_UNIT}" "${PLAN_VERSION}" "${HASH}"
detail "the sealed step exactly matches the synthetic site and business unit"
printf '{"planId":"%s","expectedVersion":%s,"expectedHash":"%s"}' "${PLAN}" "${PLAN_VERSION}" "${HASH}" \
  > "${WORKDIR}/bound.json"

python3 -c "
import json
bound = json.load(open('${WORKDIR}/bound.json'))
bound.update(idempotencyKey='demo-${RUN}-agent-approval', reason='The agent attempts to approve its own plan')
print(json.dumps(bound))" > "${WORKDIR}/agent-approval.json"
if mcp "${AGENT}" approve_action_plan < "${WORKDIR}/agent-approval.json" >/dev/null 2>&1; then
  echo "the agent approved its own plan, which must never happen" >&2
  exit 1
fi
detail "the same agent is refused approve_action_plan: the scope is not in its token"

step "6. Simulation explains the plan without touching master data"
# The reference event is delivered asynchronously, so simulation is retried until the business unit
# it names is known. The verdict itself is the proof that the inbox applied the event.
for attempt in $(seq 1 20); do
  SIMULATION="$(mcp "${AGENT}" simulate_onboarding_plan < "${WORKDIR}/bound.json")"
  OUTCOME="$(printf '%s' "${SIMULATION}" | field "['outcome']")"
  [[ "${OUTCOME}" == "EXECUTABLE" ]] && break
  sleep 2
done
printf '%s' "${SIMULATION}" | python3 -c "
import json, sys
simulation = json.load(sys.stdin)
print('    outcome', simulation['outcome'])
for step in simulation['steps']:
    print('    step', step['sequence'], step['type'], 'violations', step['violations'] or 'none')"
if [[ "${OUTCOME}" != "EXECUTABLE" ]]; then
  echo "the plan never became executable; the demo stops rather than approving a blocked change" >&2
  exit 1
fi

step "7. A separate human decides"
python3 -c "
import json
bound = json.load(open('${WORKDIR}/bound.json'))
bound.update(idempotencyKey='demo-${RUN}-approval', reason='Synthetic evidence reviewed for the demo')
print(json.dumps(bound))" > "${WORKDIR}/approval.json"
mcp "${STEWARD}" approve_action_plan < "${WORKDIR}/approval.json" \
  | python3 -c "import json,sys;d=json.load(sys.stdin);print('    status',d['status'],'decided at',d['decidedAt'])"

step "8. A third principal executes, and a retry produces no second effect"
python3 -c "
import json
bound = json.load(open('${WORKDIR}/bound.json'))
bound.update(idempotencyKey='demo-${RUN}-execution', reason='Apply the approved synthetic assignment')
print(json.dumps(bound))" > "${WORKDIR}/execution.json"
FIRST="$(mcp "${EXECUTOR}" execute_approved_plan < "${WORKDIR}/execution.json")"
REPLAY="$(mcp "${EXECUTOR}" execute_approved_plan < "${WORKDIR}/execution.json")"
detail "effect $(printf '%s' "${FIRST}" | field "['effects'][0]['eventType']") on $(printf '%s' "${FIRST}" | field "['effects'][0]['subjectType']")"
if [[ "${FIRST}" != "${REPLAY}" ]]; then
  echo "a replayed execution returned a different receipt" >&2
  exit 1
fi
detail "the replay returned the original receipt $(printf '%s' "${FIRST}" | field "['executionId']")"

step "9. The mastered change leaves through the outbox"
CORRELATION="$(printf '%s' "${FIRST}" | field "['correlationId']")"
MESSAGE=""
for _ in $(seq 1 20); do
  MESSAGE="$(docker exec stewardmesh-localstack-1 awslocal sqs receive-message \
    --queue-url "http://localhost:4566/000000000000/${STEWARDMESH_MASTER_EVENTS_QUEUE}" \
    --max-number-of-messages 10 --visibility-timeout 1 2>/dev/null \
    | python3 -c "
import json, sys
raw = sys.stdin.read().strip()
delivered = json.loads(raw) if raw else {}
for message in delivered.get('Messages', []):
    body = json.loads(message['Body'])
    if body.get('correlationId') == '${CORRELATION}':
        print(json.dumps(body))
        break" 2>/dev/null || true)"
  [[ -n "${MESSAGE}" ]] && break
  sleep 2
done
if [[ -z "${MESSAGE:-}" ]]; then
  echo "the mastered event did not reach ${STEWARDMESH_MASTER_EVENTS_QUEUE}" >&2
  exit 1
fi
printf '%s' "${MESSAGE}" | python3 -c "
import json, sys
event = json.load(sys.stdin)
print('    ', event['eventType'], 'origin', event['originSystem'], 'producer', event['producer'],
      'transport', event['transportSystem'])"
detail "a returned own event would be loop-suppressed rather than applied a second time"

printf '\n\033[1mDemo complete.\033[0m Every identity, supplier and event above was synthetic.\n'
