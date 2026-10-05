#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Build the files of a release into target/release/ (M12-06): the distribution, its SBOM, their checksums and the
# release notes. .github/workflows/release.yml runs it for a pushed tag, then publishes the image and the GitHub
# release; run it locally the same way to see what a release would contain.
#
#   release.sh v2026.1.0     the tag must be "v" + the version in every pom and package (check_versions.py), and
#                            docs/content/changelog/ must have a page titled with the version
#
# Files: grantforge-<version>.tar.gz, grantforge-<version>.sbom.json (CycloneDX, shipped dependencies only),
# SHA256SUMS and notes.md. Set GRANTFORGE_RELEASE_SKIP_TESTS=1 to skip the unit tests (CI already ran them).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

TAG="${1:?usage: release.sh <tag, such as v2026.1.0>}"
python3 script/ci/check_versions.py --tag "${TAG}"
VERSION="${TAG#v}"
OUT="${ROOT}/target/release"
rm -rf "${OUT}"
mkdir -p "${OUT}"
python3 script/ci/release_notes.py "${VERSION}" --output "${OUT}/notes.md"

MVN=(./mvnw --batch-mode --no-transfer-progress)
if [[ "${GRANTFORGE_RELEASE_SKIP_TESTS:-0}" == "1" ]]; then
  "${MVN[@]}" -DskipTests package
else
  "${MVN[@]}" verify
fi
"${MVN[@]}" --quiet -DskipFrontend -Dsbom.includeTestScope=false org.cyclonedx:cyclonedx-maven-plugin:makeAggregateBom

cp dist/grantforge-release.tar.gz "${OUT}/grantforge-${VERSION}.tar.gz"
cp target/bom.json "${OUT}/grantforge-${VERSION}.sbom.json"
(cd "${OUT}" && shasum -a 256 "grantforge-${VERSION}.tar.gz" "grantforge-${VERSION}.sbom.json" > SHA256SUMS)
echo "Release ${VERSION}:"
ls -l "${OUT}"
