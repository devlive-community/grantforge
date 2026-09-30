#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Build the MkDocs site and deploy it to GitHub Pages.
# Requires GH_TOKEN with read access to the private mkdocs-material-insiders theme repository.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}/docs"

if [[ -z "${GH_TOKEN:-}" ]]; then
  echo "GH_TOKEN is required to install the mkdocs-material-insiders theme" >&2
  exit 1
fi

pip install "git+https://${GH_TOKEN}@github.com/qianmoq/mkdocs-material-insiders.git"
pip install -r requirements.txt
mkdocs gh-deploy --force
