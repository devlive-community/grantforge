#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The Pulsar plugin and agent against real standalone brokers of Pulsar 3.0.17, 3.3.9, 4.0.14 and 4.2.5, managed by
# Testcontainers; requires a running Docker daemon.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: pulsar_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.pulsar.it.skip=false -pl "plugins/grantforge-plugin-pulsar,agents/grantforge-agent-pulsar" -am verify
