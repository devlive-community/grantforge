#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The MongoDB plugin and agent against real replica sets of MongoDB 6.0, 7.0, 8.0 and 9.0, a sharded cluster, and Percona
# Server for MongoDB's audit log and Kerberos sign-in, managed by Testcontainers; requires a running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: mongodb_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.mongodb.it.skip=false -pl "plugins/grantforge-plugin-mongodb,agents/grantforge-agent-mongodb" -am verify
