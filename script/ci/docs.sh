#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Documentation site (docs/, Next.js and Tailwind CSS) entry points used by CI (and runnable locally).
#
#   docs.sh install       install the locked dependencies
#   docs.sh check         ESLint, type check, unit tests and the content check (pages, links, images)
#   docs.sh translations  every page translated completely into every language, and every README into
#                         every language of README.md (test/translations.test.ts)
#   docs.sh build         static export to docs/out
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}/docs"

case "${1:-}" in
  install) pnpm install --frozen-lockfile ;;
  # The translation check runs as a step of its own in CI, so its findings are not buried in the unit tests.
  check)   pnpm lint && pnpm typecheck && pnpm exec vitest run --exclude test/translations.test.ts && pnpm check ;;
  translations) pnpm exec vitest run test/translations.test.ts ;;
  build)   pnpm build ;;
  *)
    echo "usage: $0 {install|check|translations|build}" >&2
    exit 2
    ;;
esac
