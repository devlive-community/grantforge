#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Versioned Hadoop daemons managed by Testcontainers; requires a running Docker daemon.
# A numbered adapter is tested only against its matching Hadoop runtime.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

if [[ $# -gt 1 ]]; then
  echo "usage: hdfs_integration.sh [2.7|2.10|3.2|3.3|3.4|3.5|all]" >&2
  exit 2
fi

SELECTED="${1:-all}"
MVN=(./mvnw --batch-mode --no-transfer-progress -DskipFrontend)
case "${SELECTED}" in
  2.7|2.10|3.2|3.3|3.4|3.5)
    MODULES="agents/grantforge-agent-hdfs-${SELECTED}"
    case "${SELECTED}" in
      2.7) PATCH="2.7.7" ;;
      2.10) PATCH="2.10.2" ;;
      3.2) PATCH="3.2.4" ;;
      3.3) PATCH="3.3.6" ;;
      3.4) PATCH="3.4.3" ;;
      3.5) PATCH="3.5.0" ;;
    esac
    MVN+=("-Dgrantforge.hdfs.it.hadoop.version=${PATCH}")
    ;;
  all)
    # Each run also exercises the single server plugin against that exact Hadoop runtime.
    for VERSION in 2.7 2.10 3.2 3.3 3.4 3.5; do
      bash "${ROOT}/script/ci/hdfs_integration.sh" "${VERSION}"
    done
    exit 0
    ;;
  *)
    echo "unsupported Hadoop line: ${SELECTED}" >&2
    exit 2
    ;;
esac

"${MVN[@]}" -pl "plugins/grantforge-plugin-hdfs,${MODULES}" -am verify
