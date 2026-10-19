#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The Trino agents' unit tests, coverage and static analysis. Their modules join the build only on JDK 25 or newer,
# which reads the SPI of every Trino line they support (script/ci/java.sh covers the rest on JDK 21).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

java_major="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')"
if [[ -z "${java_major}" || "${java_major}" -lt 25 ]]; then
  echo "trino_agents.sh needs JDK 25 or newer, not ${java_major:-unknown}" >&2
  exit 2
fi

MVN=(./mvnw --batch-mode --no-transfer-progress -DskipFrontend
  -pl agents/grantforge-agent-trino-470,agents/grantforge-agent-trino-479 -am)

case "${1:-}" in
  test)        "${MVN[@]}" clean verify -DskipITs ;;
  checkstyle)  "${MVN[@]}" checkstyle:check ;;
  spotbugs)    "${MVN[@]}" -Pstatic-analysis -DskipTests -Djacoco.skip=true test-compile spotbugs:check ;;
  pmd)         "${MVN[@]}" -Djacoco.skip=true compile pmd:check ;;
  *)
    echo "usage: $0 {test|checkstyle|spotbugs|pmd}" >&2
    exit 2
    ;;
esac
