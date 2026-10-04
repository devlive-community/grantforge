#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Build the release archive, start it against a real database with the example plugin installed and run the
# full-stack Playwright tests.
#
#   e2e_fullstack.sh [h2|postgres]      (default: h2; postgres needs Docker)
#
# Set GRANTFORGE_E2E_SKIP_BUILD=1 to reuse an existing dist/grantforge-release.tar.gz, and
# GRANTFORGE_E2E_SKIP_SAMPLES=1 to leave out the sample applications that run after the full-stack suite.
set -euo pipefail

DATABASE="${1:-h2}"
if [[ "${DATABASE}" != "h2" && "${DATABASE}" != "postgres" ]]; then
  echo "usage: $0 [h2|postgres]" >&2
  exit 2
fi
PORT="${GRANTFORGE_E2E_PORT:-19080}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${ROOT}/target/e2e-fullstack"
SERVER_PID=""
CONTAINER=""

cleanup() {
  if [[ -n "${SERVER_PID}" ]]; then kill "${SERVER_PID}" 2>/dev/null || true; wait "${SERVER_PID}" 2>/dev/null || true; fi
  if [[ -n "${CONTAINER}" ]]; then docker rm -f "${CONTAINER}" > /dev/null 2>&1 || true; fi
}
trap cleanup EXIT

cd "${ROOT}"
if [[ "${GRANTFORGE_E2E_SKIP_BUILD:-0}" != "1" ]]; then
  ./mvnw --batch-mode --no-transfer-progress -DskipTests clean package
fi

rm -rf "${WORK}"
mkdir -p "${WORK}"
tar -xzf dist/grantforge-release.tar.gz -C "${WORK}"
HOME_DIR="${WORK}/grantforge"
# Install the example plugin, built against the plugin API alone, so the tests can use services, policies and agents.
mkdir -p "${HOME_DIR}/plugins"
cp plugins/grantforge-plugin-example/target/grantforge-plugin-example-*.jar "${HOME_DIR}/plugins/"

case "${DATABASE}" in
  h2)
    export GRANTFORGE_DB_URL="jdbc:h2:file:${HOME_DIR}/data/grantforge"
    ;;
  postgres)
    CONTAINER="grantforge-e2e-postgres-$$"
    docker run -d --rm --name "${CONTAINER}" -e POSTGRES_DB=grantforge -e POSTGRES_USER=grantforge \
      -e POSTGRES_PASSWORD=grantforge -p 127.0.0.1::5432 postgres:17-alpine > /dev/null
    for _ in $(seq 1 60); do
      docker exec "${CONTAINER}" pg_isready -U grantforge -d grantforge > /dev/null 2>&1 && break
      sleep 1
    done
    DB_PORT="$(docker port "${CONTAINER}" 5432/tcp | head -n 1 | sed 's/.*://')"
    export GRANTFORGE_DB_URL="jdbc:postgresql://127.0.0.1:${DB_PORT}/grantforge"
    export GRANTFORGE_DB_USER=grantforge GRANTFORGE_DB_PASSWORD=grantforge
    ;;
esac

export GRANTFORGE_HOME="${HOME_DIR}" GRANTFORGE_SERVER_PORT="${PORT}"
(cd "${HOME_DIR}" && exec java -classpath "lib/*" org.devlive.grantforge.server.GrantForge \
  --spring.config.additional-location="${HOME_DIR}/configure/" > "${WORK}/server.log" 2>&1) &
SERVER_PID=$!

BASE_URL="http://127.0.0.1:${PORT}"
READY_URL="${BASE_URL}/actuator/health/readiness"
for _ in $(seq 1 90); do
  curl --silent --fail --output /dev/null "${READY_URL}" && break
  if ! kill -0 "${SERVER_PID}" 2>/dev/null; then cat "${WORK}/server.log" >&2; exit 1; fi
  sleep 1
done
curl --silent --fail --output /dev/null "${READY_URL}" || { cat "${WORK}/server.log" >&2; exit 1; }

# A fresh installation logs a one-time setup token before it reports ready; the tests use it the way an
# operator would, by reading it from the log.
SETUP_TOKEN="$(grep -o 'enter this setup token: [A-Za-z0-9_-]*' "${WORK}/server.log" | tail -n 1 | sed 's/.*: //')"
if [[ -z "${SETUP_TOKEN}" ]]; then
  echo "the server did not log a setup token" >&2
  cat "${WORK}/server.log" >&2
  exit 1
fi

cd "${ROOT}/core/grantforge-web"
GRANTFORGE_BASE_URL="${BASE_URL}" GRANTFORGE_E2E_SETUP_TOKEN="${SETUP_TOKEN}" pnpm exec playwright test --config playwright.fullstack.config.ts

# The sample applications, against the same server, which the suite above has set up: the starter and the JavaScript
# client as an application takes them, the samples built apart from GrantForge.
if [[ "${GRANTFORGE_E2E_SKIP_SAMPLES:-0}" != "1" ]]; then
  cd "${ROOT}"
  MAVEN=(./mvnw --batch-mode --no-transfer-progress --quiet -DskipTests)
  "${MAVEN[@]}" --non-recursive install
  "${MAVEN[@]}" -pl sdk/grantforge-spring-boot-starter install
  "${MAVEN[@]}" -f samples/pom.xml package
  bash script/ci/sdk_js.sh install
  bash script/ci/sdk_js.sh build
  cd "${ROOT}/core/grantforge-web"
  GRANTFORGE_BASE_URL="${BASE_URL}" pnpm exec playwright test --config playwright.samples.config.ts
fi
