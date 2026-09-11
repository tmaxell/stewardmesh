#!/usr/bin/env bash
set -euo pipefail

script_directory="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
repository_root="$(cd -- "${script_directory}/.." && pwd)"
cd "${repository_root}"

java_feature="$(java -XshowSettings:properties -version 2>&1 \
    | awk -F '= ' '/java.specification.version/ {print $2; exit}')"
if [[ "${java_feature}" != "25" ]]; then
    echo "Java 25 is required; active Java specification version is ${java_feature:-unknown}." >&2
    exit 1
fi

command -v docker >/dev/null 2>&1 || {
    echo "Docker is required for the Phase 2 integration tests." >&2
    exit 1
}

./mvnw --batch-mode --no-transfer-progress verify
docker compose -f deploy/local/compose.yaml config --quiet
git diff --check

if [[ -n "$(git ls-files docs .agents)" ]]; then
    echo "Ignored docs/ or .agents/ content is present in the Git index." >&2
    exit 1
fi

e2e_report="master-data-service/bootstrap-master-service/target/failsafe-reports/TEST-io.stewardmesh.masterdata.bootstrap.IdentityResolutionEndToEndIT.xml"
coverage_csv="master-data-service/bootstrap-master-service/target/site/jacoco-aggregate/jacoco.csv"
[[ -f "${e2e_report}" ]] || {
    echo "Phase 2 E2E report was not generated." >&2
    exit 1
}
[[ -f "${coverage_csv}" ]] || {
    echo "Aggregate JaCoCo report was not generated." >&2
    exit 1
}

coverage_summary="$(awk -F, '
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
' "${coverage_csv}")" || {
    echo "Aggregate line coverage is below the 80% Phase 2 review floor." >&2
    exit 1
}

echo "Phase 2 identity resolution verification passed (${coverage_summary})."
