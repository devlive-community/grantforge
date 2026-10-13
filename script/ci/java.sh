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
#   java.sh pmd             PMD with configure/pmd-ruleset.xml over production sources
#
# spotbugs and pmd analyse the classes "test" or "compile-tests" built, without running the tests again; on a
# fresh checkout they compile first.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

MVN=(./mvnw --batch-mode --no-transfer-progress -DskipFrontend)

case "${1:-}" in
  test)           "${MVN[@]}" clean test ;;
  compile-tests)  "${MVN[@]}" test ;;
  checkstyle)     "${MVN[@]}" checkstyle:check ;;
  spotbugs)       "${MVN[@]}" -Pstatic-analysis -DskipTests -Djacoco.skip=true test-compile spotbugs:check ;;
  pmd)            "${MVN[@]}" -Djacoco.skip=true compile pmd:check ;;
  *)
    echo "usage: $0 {test|compile-tests|checkstyle|spotbugs|pmd}" >&2
    exit 2
    ;;
esac
