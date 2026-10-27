#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The Elasticsearch plugin and agent against real clusters of Elasticsearch 8.19, 9.3, 9.4 and 9.5 with security,
# managed by Testcontainers; requires a running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: elasticsearch_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.elasticsearch.it.skip=false -pl "plugins/grantforge-plugin-elasticsearch,agents/grantforge-agent-elasticsearch" -am verify
