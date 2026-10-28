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
  test)
    "${MVN[@]}" clean test
    # The datasource's integration tests run in the assembled server. Include both modules' execution data
    # in its report so the coverage gate measures all tests that actually exercise the datasource classes.
    jacoco_version="$(python3 -c 'import xml.etree.ElementTree as e; r=e.parse("pom.xml").getroot(); print(r.find("{*}properties/{*}plugin.jacoco.version").text)')"
    dependency_version="$(python3 -c 'import xml.etree.ElementTree as e; r=e.parse("pom.xml").getroot(); print(r.find("{*}properties/{*}plugin.dependency.version").text)')"
    "${MVN[@]}" -N "org.apache.maven.plugins:maven-dependency-plugin:${dependency_version}:copy" \
      "-Dartifact=org.jacoco:org.jacoco.cli:${jacoco_version}:jar:nodeps" \
      "-DoutputDirectory=${ROOT}/target/coverage-tools" -Dmdep.stripVersion=true
    java -jar target/coverage-tools/org.jacoco.cli-nodeps.jar report \
      core/grantforge-datasource/target/jacoco.exec core/grantforge-server/target/jacoco.exec \
      --classfiles core/grantforge-datasource/target/classes \
      --sourcefiles core/grantforge-datasource/src/main/java \
      --xml core/grantforge-datasource/target/site/jacoco/jacoco.xml
    ;;
  compile-tests)  "${MVN[@]}" test ;;
  checkstyle)     "${MVN[@]}" checkstyle:check ;;
  spotbugs)       "${MVN[@]}" -Pstatic-analysis -DskipTests -Djacoco.skip=true test-compile spotbugs:check ;;
  pmd)            "${MVN[@]}" -Djacoco.skip=true compile pmd:check ;;
  *)
    echo "usage: $0 {test|compile-tests|checkstyle|spotbugs|pmd}" >&2
    exit 2
    ;;
esac
