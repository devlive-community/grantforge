#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Run the multi-database integration tests against one database (needs Docker except for h2).
#
#   db_integration.sh h2 | postgres:<v> | mysql:<v> | mariadb:<v> | oracle:<v> | sqlserver:<v>
set -euo pipefail

if [[ $# -ne 1 || -z "$1" ]]; then
  echo "usage: $0 <database>   e.g. h2, postgres:17, mysql:8.4, mariadb:11.4, oracle:23, sqlserver:2022" >&2
  exit 2
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

./mvnw --batch-mode --no-transfer-progress -DskipFrontend -Pdatabase-it \
  -pl core/grantforge-persistence,core/grantforge-audit,core/grantforge-identity,core/grantforge-server -am verify "-Dgrantforge.it.database=$1"
