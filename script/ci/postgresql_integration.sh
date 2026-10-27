#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The PostgreSQL plugin and agent against real servers of PostgreSQL 15, 16, 17 and 18, the agent's with pgaudit, managed
# by Testcontainers; requires a running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: postgresql_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.postgresql.it.skip=false -pl "plugins/grantforge-plugin-postgresql,agents/grantforge-agent-postgresql" -am verify
