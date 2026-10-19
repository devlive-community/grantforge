#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The Trino plugin against real Trino 470, 476, 479 and 484 coordinators, also over HTTPS with a password file, JWTs and
# Kerberos, managed by Testcontainers; requires a running Docker daemon. On JDK 25 or newer, which builds the Trino
# agents, their jars are verified in the same coordinators too: the one for 470 to 478 in 470 and 476, the one for 479
# and later in 479 and 484.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: trino_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

modules=plugins/grantforge-plugin-trino
java_major="$(java -XshowSettings:properties -version 2>&1 | sed -n 's/^ *java.specification.version = //p')"
if [[ -n "${java_major}" && "${java_major}" -ge 25 ]]; then
  modules+=",agents/grantforge-agent-trino-470,agents/grantforge-agent-trino-479"
fi

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.trino.it.skip=false -pl "${modules}" -am verify
