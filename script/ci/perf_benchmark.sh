#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Run the performance benchmarks (M12-01): the JMH benchmark of grant derivation and the system benchmark that
# seeds a server at scale and measures authorization snapshots and list APIs against perf/thresholds.properties.
#
#   perf_benchmark.sh full [database]    the target scale (a million accounts); fails on an exceeded threshold
#                                        (default database: postgres:17, which needs Docker)
#   perf_benchmark.sh smoke [database]   a small scale that only checks the benchmarks still run (default: h2)
#
# Further perf.* settings (see perf/README.md) pass through PERF_OPTS, for example PERF_OPTS="-Dperf.samples=200".
# Set GRANTFORGE_PERF_SKIP_BUILD=1 to reuse the installed modules. The report is perf/target/perf-report.json.
set -euo pipefail

MODE="${1:-smoke}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

case "${MODE}" in
  full)
    DATABASE="${2:-postgres:17}"
    SCALE=(-Dperf.users=1000000 -Dperf.roles=10000 -Dperf.resources=100000 -Dperf.samples=1000 -Dperf.warmup=200 -Dperf.enforce=true)
    ;;
  smoke)
    DATABASE="${2:-h2}"
    SCALE=(-Dperf.users=5000 -Dperf.roles=200 -Dperf.resources=2000 -Dperf.samples=50 -Dperf.warmup=10 -Dperf.enforce=false)
    ;;
  *)
    echo "usage: $0 {full|smoke} [database]" >&2
    exit 2
    ;;
esac

MVN=(./mvnw --batch-mode --no-transfer-progress -DskipFrontend)
if [[ "${GRANTFORGE_PERF_SKIP_BUILD:-0}" != "1" ]]; then
  "${MVN[@]}" -DskipTests install
fi
# The harness has unit tests of its own; the class path file lets the JMH runner fork with the same classes.
"${MVN[@]}" -f perf/pom.xml test dependency:build-classpath -Dmdep.outputFile=target/classpath.txt

# shellcheck disable=SC2086 # PERF_OPTS holds separate -D options
java -Xms1g -Xmx4g -cp "perf/target/classes:$(cat perf/target/classpath.txt)" "${SCALE[@]}" -Dperf.database="${DATABASE}" \
  -Dperf.thresholds=perf/thresholds.properties -Dperf.report=perf/target/perf-report.json ${PERF_OPTS:-} \
  org.devlive.grantforge.perf.PerfBenchmark
