#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Real Hadoop daemons managed by Testcontainers; requires a running Docker daemon.
# Builds the release agent jar and verifies permissions, signed caching and manual HA failover.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

if [[ $# -ne 0 ]]; then
  echo "usage: hdfs_integration.sh" >&2
  exit 2
fi

./mvnw --batch-mode --no-transfer-progress -DskipFrontend -Phdfs-it \
  -pl agents/grantforge-agent-hdfs -am verify
