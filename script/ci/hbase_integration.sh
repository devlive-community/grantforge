#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The HBase plugin and agent against real HBase 2.5, 2.6 and 3.0 clusters, the plugin also with Kerberos, managed by
# Testcontainers; requires a running Docker daemon, which builds each release's test image from its SHA-512-verified
# Apache distribution once.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: hbase_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.hbase.it.skip=false \
  -pl plugins/grantforge-plugin-hbase,agents/grantforge-agent-hbase -am verify
