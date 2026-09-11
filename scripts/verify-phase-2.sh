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
./scripts/check-quality-gates.sh
