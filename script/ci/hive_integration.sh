#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The Hive plugin and the Hive agent against real HiveServer2 3.1, 4.0 and 4.1 servers managed by Testcontainers;
# requires a running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: hive_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.hive.it.skip=false \
  -pl plugins/grantforge-plugin-hive,agents/grantforge-agent-hive -am verify
