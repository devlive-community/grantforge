#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The Redis plugin and agent against real primaries with a replica of Redis 7.2, 7.4, 8.2 and 8.10 and Valkey 8.1 and 9.1,
# and a Redis cluster, managed by Testcontainers; requires a running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: redis_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.redis.it.skip=false -pl "plugins/grantforge-plugin-redis,agents/grantforge-agent-redis" -am verify
