#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Build the server image and start it with a database from deploy/compose, as an operator would; then complete the
# first-run setup, sign in, restart the server and sign in again, so the image, the compose example and the
# migrations on that database are all exercised.
#
#   docker_smoke.sh [h2|postgres|mariadb|mysql|sqlserver|oracle]      (default: postgres)
#
# Set GRANTFORGE_SMOKE_SKIP_BUILD=1 to reuse dist/grantforge-release.tar.gz, and GRANTFORGE_SMOKE_PORT to change the
# published port (default 19090).
set -euo pipefail

DATABASE="${1:-postgres}"
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
COMPOSE_FILE="${ROOT}/deploy/compose/${DATABASE}.yml"
if [[ ! -f "${COMPOSE_FILE}" ]]; then
  echo "usage: $0 [h2|postgres|mariadb|mysql|sqlserver|oracle]" >&2
  exit 2
fi
cd "${ROOT}"

PORT="${GRANTFORGE_SMOKE_PORT:-19090}"
BASE_URL="http://127.0.0.1:${PORT}"
WORK="${ROOT}/target/docker-smoke"
PROJECT="grantforge-smoke-${DATABASE}"
PASSWORD="a long enough smoke password"
export GRANTFORGE_IMAGE="grantforge:smoke" GRANTFORGE_PORT="${PORT}" GRANTFORGE_DB_PASSWORD="Smoke-test-2026"
GRANTFORGE_SETUP_TOKEN="smoke-$(od -An -N12 -tx1 /dev/urandom | tr -d ' \n')"
export GRANTFORGE_SETUP_TOKEN
COMPOSE=(docker compose -p "${PROJECT}" -f "${COMPOSE_FILE}")

cleanup() {
  "${COMPOSE[@]}" logs grantforge > "${WORK}/server.log" 2>&1 || true
  "${COMPOSE[@]}" down --volumes --remove-orphans > /dev/null 2>&1 || true
}
rm -rf "${WORK}"
mkdir -p "${WORK}"
trap cleanup EXIT

if [[ "${GRANTFORGE_SMOKE_SKIP_BUILD:-0}" != "1" ]]; then
  ./mvnw --batch-mode --no-transfer-progress -DskipTests package
fi
docker build --quiet -f deploy/docker/Dockerfile -t "${GRANTFORGE_IMAGE}" dist > /dev/null

if [[ "${DATABASE}" == "mysql" ]]; then
  # The GPL driver is not part of GrantForge; fetch the version Spring Boot manages, as a user would add it. The
  # Boot BOM is imported, so its version properties are not visible: read the version the tests resolve instead.
  ./mvnw --batch-mode --no-transfer-progress --quiet -pl core/grantforge-test-support dependency:list \
    -DincludeArtifactIds=mysql-connector-j "-DoutputFile=${WORK}/mysql-driver.txt"
  VERSION="$(sed -n 's/.*com\.mysql:mysql-connector-j:jar:\([^:]*\):.*/\1/p' "${WORK}/mysql-driver.txt" | head -1)"
  if [[ -z "${VERSION}" ]]; then
    echo "could not find the version of mysql-connector-j" >&2
    exit 1
  fi
  ./mvnw --batch-mode --no-transfer-progress --quiet dependency:copy "-Dartifact=com.mysql:mysql-connector-j:${VERSION}" \
    "-DoutputDirectory=${WORK}/drivers"
  export GRANTFORGE_DRIVERS="${WORK}/drivers"
fi

"${COMPOSE[@]}" up --detach --quiet-pull

wait_ready() {
  for _ in $(seq 1 300); do
    curl --silent --fail --output /dev/null "${BASE_URL}/actuator/health/readiness" && return 0
    sleep 2
  done
  echo "the server did not become ready" >&2
  "${COMPOSE[@]}" logs grantforge >&2 || true
  return 1
}

# Signs in with a fresh cookie jar and prints the signed-in user.
sign_in() {
  local jar="${WORK}/cookies-$1.txt"
  curl --silent --fail --cookie-jar "${jar}" --cookie "${jar}" --output /dev/null "${BASE_URL}/api/v1/bootstrap"
  local xsrf
  xsrf="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "${jar}")"
  curl --silent --fail --cookie-jar "${jar}" --cookie "${jar}" --header "X-XSRF-TOKEN: ${xsrf}" \
    --header 'Content-Type: application/json' --output /dev/null \
    --data "{\"username\": \"admin\", \"password\": \"${PASSWORD}\"}" "${BASE_URL}/api/v1/auth/login"
  curl --silent --fail --cookie "${jar}" "${BASE_URL}/api/v1/me"
}

wait_ready
JAR="${WORK}/cookies-setup.txt"
curl --silent --fail --cookie-jar "${JAR}" --output /dev/null "${BASE_URL}/api/v1/bootstrap"
XSRF="$(awk '$6 == "XSRF-TOKEN" { print $7 }' "${JAR}")"
curl --silent --fail --cookie-jar "${JAR}" --cookie "${JAR}" --header "X-XSRF-TOKEN: ${XSRF}" \
  --header 'Content-Type: application/json' --output /dev/null \
  --data "{\"token\": \"${GRANTFORGE_SETUP_TOKEN}\", \"username\": \"admin\", \"password\": \"${PASSWORD}\"}" "${BASE_URL}/api/v1/setup"
sign_in first | grep -q '"username":"admin"'

# The account must survive a restart of the server: it lives in the database, not in the container.
"${COMPOSE[@]}" restart grantforge > /dev/null
wait_ready
sign_in second | grep -q '"username":"admin"'
echo "docker smoke ok on ${DATABASE}"
