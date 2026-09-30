#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# Web (core/grantforge-web) entry points used by CI (and runnable locally).
#
#   web.sh install        install the locked dependencies
#   web.sh build          type check and production build
#   web.sh lint           ESLint with zero warnings allowed
#   web.sh test           Vitest unit and contract tests
#   web.sh e2e-browsers   install the Playwright Chromium browser and its system dependencies
#   web.sh e2e            Playwright browser acceptance tests
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}/core/grantforge-web"

case "${1:-}" in
  install)      pnpm install --frozen-lockfile ;;
  build)        pnpm build ;;
  lint)         pnpm lint ;;
  test)         pnpm test ;;
  e2e-browsers) pnpm exec playwright install --with-deps chromium ;;
  e2e)          pnpm test:e2e ;;
  *)
    echo "usage: $0 {install|build|lint|test|e2e-browsers|e2e}" >&2
    exit 2
    ;;
esac
