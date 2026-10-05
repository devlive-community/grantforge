#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Take the screenshots of the documentation: build the release archive, start it on a fresh H2 database with the
# example plugin installed, and let tests/docs/screenshots.spec.ts set it up, seed a demo company and capture the
# pages into docs/public/screenshots.
#
# Set GRANTFORGE_DOCS_SKIP_BUILD=1 to reuse an existing dist/grantforge-release.tar.gz, and
# GRANTFORGE_DOCS_SCREENSHOTS to write the pictures elsewhere.
set -euo pipefail

PORT="${GRANTFORGE_DOCS_PORT:-19090}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
WORK="${ROOT}/target/docs-screenshots"
SERVER_PID=""

cleanup() {
  if [[ -n "${SERVER_PID}" ]]; then kill "${SERVER_PID}" 2>/dev/null || true; wait "${SERVER_PID}" 2>/dev/null || true; fi
}
trap cleanup EXIT

cd "${ROOT}"
if [[ "${GRANTFORGE_DOCS_SKIP_BUILD:-0}" != "1" ]]; then
  ./mvnw --batch-mode --no-transfer-progress -DskipTests clean package
fi

rm -rf "${WORK}"
mkdir -p "${WORK}"
tar -xzf dist/grantforge-release.tar.gz -C "${WORK}"
HOME_DIR="${WORK}/grantforge"
mkdir -p "${HOME_DIR}/plugins"
cp plugins/grantforge-plugin-example/target/grantforge-plugin-example-*.jar "${HOME_DIR}/plugins/"

export GRANTFORGE_DB_URL="jdbc:h2:file:${HOME_DIR}/data/grantforge"
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

SETUP_TOKEN="$(grep -o 'enter this setup token: [A-Za-z0-9_-]*' "${WORK}/server.log" | tail -n 1 | sed 's/.*: //')"
if [[ -z "${SETUP_TOKEN}" ]]; then
  echo "the server did not log a setup token" >&2
  cat "${WORK}/server.log" >&2
  exit 1
fi

cd "${ROOT}/core/grantforge-web"
GRANTFORGE_BASE_URL="${BASE_URL}" GRANTFORGE_E2E_SETUP_TOKEN="${SETUP_TOKEN}" \
  GRANTFORGE_DOCS_SCREENSHOTS="${GRANTFORGE_DOCS_SCREENSHOTS:-${ROOT}/docs/public/screenshots}" \
  pnpm exec playwright test --config playwright.docs.config.ts
