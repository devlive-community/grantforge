#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Maven entry points used by CI (and runnable locally with the same arguments).
#
#   java.sh test            clean build and unit tests of every Java module
#   java.sh compile-tests   build and unit tests without clean (static analysis input)
#   java.sh checkstyle      Checkstyle over main and test sources
#   java.sh spotbugs        SpotBugs with the static-analysis profile
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

MVN=(./mvnw --batch-mode --no-transfer-progress -DskipFrontend)

case "${1:-}" in
  test)           "${MVN[@]}" clean test ;;
  compile-tests)  "${MVN[@]}" test ;;
  checkstyle)     "${MVN[@]}" checkstyle:check ;;
  spotbugs)       "${MVN[@]}" -Pstatic-analysis test spotbugs:check ;;
  *)
    echo "usage: $0 {test|compile-tests|checkstyle|spotbugs}" >&2
    exit 2
    ;;
esac
