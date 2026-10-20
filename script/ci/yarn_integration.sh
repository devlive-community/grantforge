#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The YARN plugin against real ResourceManagers of Hadoop 3.2.4, 3.3.6, 3.4.3 and 3.5.0, the plugin also with Kerberos,
# managed by Testcontainers; requires a running Docker daemon, which builds the 3.2.4 image from its SHA-512-verified
# Apache distribution once.
set -euo pipefail

if [[ $# -ne 0 ]]; then
  echo "usage: yarn_integration.sh" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Dgrantforge.yarn.it.skip=false -pl plugins/grantforge-plugin-yarn -am verify
