#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The Solr plugin and agent against real SolrCloud nodes of Solr 9.8.1, 9.10.1, 9.11.0 and 10.0.0, managed by
# Testcontainers; requires a running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: solr_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.solr.it.skip=false -pl "plugins/grantforge-plugin-solr,agents/grantforge-agent-solr" -am verify
