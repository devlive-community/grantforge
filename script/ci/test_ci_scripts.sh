#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Run the unit tests of the CI helper scripts in script/ci/.
# Uses only the Python standard library (unittest), so no packages need to be installed.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

# Keep bytecode out of the working tree so the header/forbidden-path checks stay clean.
export PYTHONDONTWRITEBYTECODE=1

python3 -m unittest discover --start-directory script/ci/tests --pattern 'test_*.py' --verbose
