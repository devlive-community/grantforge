#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# The Maven command of the CI scripts, which source this file: MVN=(./mvnw --batch-mode --no-transfer-progress ...).
#
# Jobs that run after the gate job (java.sh in the "Java 21" job of ci.yml) passed on the same commit set
# GRANTFORGE_CHECKED=1. The gate ran the unit tests, coverage, NullAway, the enforcer rules, Checkstyle, SpotBugs
# and PMD, so such jobs build without them: no unit tests (integration tests still run), no JaCoCo agent, no
# enforcer and no Error Prone. Without the variable, as on a developer's machine, nothing is skipped.
# shellcheck disable=SC2034 # used by the scripts that source this file
MVN=(./mvnw --batch-mode --no-transfer-progress)
if [[ "${GRANTFORGE_CHECKED:-0}" == "1" ]]; then
  MVN+=(-Dtest=none -Dsurefire.failIfNoSpecifiedTests=false -Djacoco.skip=true -Denforcer.skip=true "-P!null-safety")
fi
