#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Documentation site (docs/, Next.js and Tailwind CSS) entry points used by CI (and runnable locally).
#
#   docs.sh install   install the locked dependencies
#   docs.sh check     ESLint, type check, unit tests and the content check (pages, links, images)
#   docs.sh build     static export to docs/out
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}/docs"

case "${1:-}" in
  install) pnpm install --frozen-lockfile ;;
  check)   pnpm lint && pnpm typecheck && pnpm test && pnpm check ;;
  build)   pnpm build ;;
  *)
    echo "usage: $0 {install|check|build}" >&2
    exit 2
    ;;
esac
