#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The MySQL plugin and proxy agent against real MySQL 8.0 and 8.4 servers managed by Testcontainers; requires a
# running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: mysql_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

./mvnw --batch-mode --no-transfer-progress -DskipFrontend -Dgrantforge.mysql.it.skip=false \
  -pl plugins/grantforge-plugin-mysql,agents/grantforge-agent-mysql -am verify
