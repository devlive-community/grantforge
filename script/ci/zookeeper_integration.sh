#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Real ZooKeeper servers managed by Testcontainers; requires a running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: zookeeper_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

./mvnw --batch-mode --no-transfer-progress -DskipFrontend -Dgrantforge.zookeeper.it.skip=false \
  -pl plugins/grantforge-plugin-zookeeper -am verify
