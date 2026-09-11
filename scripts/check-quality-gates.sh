#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIRECTORY="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPOSITORY_ROOT="$(cd -- "${SCRIPT_DIRECTORY}/.." && pwd)"
cd "${REPOSITORY_ROOT}"

docker compose --env-file .env.example -f deploy/local/compose.yaml config --quiet
git diff --check

if [[ -n "$(git ls-files docs .agents)" ]]; then
  echo "Ignored docs/ or .agents/ content is present in the Git index." >&2
  exit 1
fi

for report in \
  master-data-service/bootstrap-master-service/target/failsafe-reports/TEST-io.stewardmesh.masterdata.bootstrap.IdentityResolutionEndToEndIT.xml \
  master-data-service/bootstrap-master-service/target/failsafe-reports/TEST-io.stewardmesh.masterdata.bootstrap.SupplierIntakeEndToEndIT.xml; do
  [[ -f "${report}" ]] || {
    echo "Required E2E report was not generated: ${report}" >&2
    exit 1
  }
done

COVERAGE_CSV="master-data-service/bootstrap-master-service/target/site/jacoco-aggregate/jacoco.csv"
[[ -f "${COVERAGE_CSV}" ]] || {
  echo "Aggregate JaCoCo report was not generated." >&2
  exit 1
}

COVERAGE_SUMMARY="$(awk -F, '
    NR > 1 {
        instruction_missed += $4; instruction_covered += $5
        branch_missed += $6; branch_covered += $7
        line_missed += $8; line_covered += $9
    }
    END {
        instruction_total = instruction_missed + instruction_covered
        branch_total = branch_missed + branch_covered
        line_total = line_missed + line_covered
        instruction_percent = instruction_total ? 100 * instruction_covered / instruction_total : 0
        branch_percent = branch_total ? 100 * branch_covered / branch_total : 0
        line_percent = line_total ? 100 * line_covered / line_total : 0
        printf "instructions=%.1f%% branches=%.1f%% lines=%.1f%%", \
            instruction_percent, branch_percent, line_percent
        if (line_percent < 80) exit 2
    }
' "${COVERAGE_CSV}")" || {
  echo "Aggregate line coverage is below the 80% Phase 2 review floor." >&2
  exit 1
}

echo "Repository quality gates passed (${COVERAGE_SUMMARY})."
