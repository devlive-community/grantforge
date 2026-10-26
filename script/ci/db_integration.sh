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

# The database matrix does not run the HDFS, ZooKeeper, MySQL, Hive, Kafka, HBase, Trino, YARN, Pulsar, Solr, ClickHouse or
# PostgreSQL container suites.
# shellcheck source=script/ci/maven.sh
source "$(dirname "${BASH_SOURCE[0]}")/maven.sh"
"${MVN[@]}" -DskipFrontend -Pdatabase-it -Dgrantforge.bundle.skip=true \
  -Dgrantforge.hdfs.it.skip=true -Dgrantforge.zookeeper.it.skip=true -Dgrantforge.mysql.it.skip=true \
  -Dgrantforge.hive.it.skip=true -Dgrantforge.kafka.it.skip=true -Dgrantforge.hbase.it.skip=true -Dgrantforge.trino.it.skip=true -Dgrantforge.yarn.it.skip=true \
  -Dgrantforge.pulsar.it.skip=true -Dgrantforge.solr.it.skip=true -Dgrantforge.clickhouse.it.skip=true \
  -Dgrantforge.postgresql.it.skip=true \
  -pl core/grantforge-persistence,core/grantforge-audit,core/grantforge-identity,core/grantforge-authz,core/grantforge-server -am verify "-Dgrantforge.it.database=$1"
