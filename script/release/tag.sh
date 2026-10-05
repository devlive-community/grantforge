#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Cut a release (D-90): set the version everywhere, tag v<version> and push it. The tag starts
# .github/workflows/release.yml, which builds and publishes the distribution, the Docker image, the Maven artifacts
# (GitHub Packages, and Maven Central when its secrets are set) and the GitHub release, whose notes are the commits
# since the previous release.
#
#   tag.sh 2026.1.0                      release 2026.1.0 from the current branch (dev by default)
#   tag.sh 2026.1.0 --next 2026.2.0      then set the version the branch continues with
#   tag.sh 2026.1.0 --dry-run            check everything and show the notes; change nothing
#
# Options: --remote NAME (origin), --branch NAME (dev; the branch releases are cut from), --yes (do not ask).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}"

usage() {
  echo "usage: $0 <version> [--next <version>] [--remote <name>] [--branch <name>] [--dry-run] [--yes]" >&2
  exit 2
}

[[ $# -ge 1 ]] || usage
VERSION="$1"
shift
NEXT=""
REMOTE="origin"
BRANCH="dev"
DRY_RUN=0
ASSUME_YES=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --next) [[ $# -ge 2 ]] || usage; NEXT="$2"; shift 2 ;;
    --remote) [[ $# -ge 2 ]] || usage; REMOTE="$2"; shift 2 ;;
    --branch) [[ $# -ge 2 ]] || usage; BRANCH="$2"; shift 2 ;;
    --dry-run) DRY_RUN=1; shift ;;
    --yes) ASSUME_YES=1; shift ;;
    *) usage ;;
  esac
done
TAG="v${VERSION}"

fail() {
  echo "error: $*" >&2
  exit 1
}

pattern='^[0-9]{4}\.[0-9]+\.[0-9]+(-rc\.[0-9]+)?$'
[[ "${VERSION}" =~ ${pattern} ]] || fail "${VERSION} is not YEAR.MINOR.PATCH, optionally with -rc.N"
[[ -z "${NEXT}" || "${NEXT}" =~ ${pattern} ]] || fail "${NEXT} is not YEAR.MINOR.PATCH, optionally with -rc.N"
[[ -z "$(git status --porcelain)" ]] || fail "the working tree has changes; commit or stash them first"
CURRENT_BRANCH="$(git symbolic-ref --quiet --short HEAD || true)"
[[ "${CURRENT_BRANCH}" == "${BRANCH}" ]] || fail "releases are cut from ${BRANCH}, not ${CURRENT_BRANCH:-a detached HEAD} (--branch to change)"

git fetch --quiet --tags "${REMOTE}" "${BRANCH}"
if ! git merge-base --is-ancestor "${REMOTE}/${BRANCH}" HEAD; then
  fail "${BRANCH} is behind ${REMOTE}/${BRANCH}; pull first"
fi
if git rev-parse --quiet --verify "refs/tags/${TAG}" > /dev/null || [[ -n "$(git ls-remote --tags "${REMOTE}" "refs/tags/${TAG}")" ]]; then
  fail "${TAG} exists already"
fi

CURRENT_VERSION="$(python3 script/ci/check_versions.py --show)"
echo "Releasing ${TAG} from ${BRANCH} (version now ${CURRENT_VERSION})"
echo
python3 script/ci/release_notes.py "${TAG}" --head HEAD | head -40
echo "..."
echo

if [[ "${DRY_RUN}" == "1" ]]; then
  echo "Dry run: nothing changed. Without --dry-run this would:"
  [[ "${CURRENT_VERSION}" == "${VERSION}" ]] || echo "  - set the version to ${VERSION} and commit it"
  echo "  - tag ${TAG} and push ${BRANCH} and ${TAG} to ${REMOTE}"
  [[ -z "${NEXT}" ]] || echo "  - set the version to ${NEXT}, commit and push it"
  exit 0
fi

if [[ "${ASSUME_YES}" != "1" ]]; then
  read -r -p "Tag ${TAG} and push it to ${REMOTE}, which publishes the release? [y/N] " answer
  [[ "${answer}" == "y" || "${answer}" == "Y" ]] || fail "cancelled"
fi

if [[ "${CURRENT_VERSION}" != "${VERSION}" ]]; then
  python3 script/ci/check_versions.py --set "${VERSION}"
  git commit --quiet --all --message "chore(release): prepare ${VERSION}"
fi
python3 script/ci/check_versions.py --tag "${TAG}"
git tag --annotate "${TAG}" --message "GrantForge ${VERSION}"
git push --quiet "${REMOTE}" "${BRANCH}"
git push --quiet "${REMOTE}" "${TAG}"
echo "Pushed ${TAG}; the release workflow builds and publishes it."

if [[ -n "${NEXT}" ]]; then
  python3 script/ci/check_versions.py --set "${NEXT}"
  git commit --quiet --all --message "chore(release): start ${NEXT}"
  git push --quiet "${REMOTE}" "${BRANCH}"
  echo "${BRANCH} continues with ${NEXT}."
fi

REPOSITORY="$(git remote get-url "${REMOTE}" | sed -E 's#^.*github\.com[:/]##; s#\.git$##')"
echo "Follow it at https://github.com/${REPOSITORY}/actions/workflows/release.yml"
