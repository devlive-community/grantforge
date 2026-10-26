#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The OpenSearch plugin against real clusters of OpenSearch 2.19, 3.3, 3.6 and 3.9 with the security plugin,
# managed by Testcontainers; requires a running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: opensearch_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.opensearch.it.skip=false -pl "plugins/grantforge-plugin-opensearch" -am verify
