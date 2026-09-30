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
#   web.sh api-check      fail when src/api/schema.d.ts is stale against src/api/openapi.json
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
  api-check)
    generated="$(mktemp -t grantforge-schema.XXXXXX).d.ts"
    trap 'rm -f "${generated}"' EXIT
    pnpm exec openapi-typescript src/api/openapi.json --output "${generated}" > /dev/null
    if ! cmp -s "${generated}" src/api/schema.d.ts; then
      echo "src/api/schema.d.ts is out of date with src/api/openapi.json; run: pnpm api:generate" >&2
      exit 1
    fi
    echo "API types match the OpenAPI contract"
    ;;
  *)
    echo "usage: $0 {install|build|lint|test|e2e-browsers|e2e|api-check}" >&2
    exit 2
    ;;
esac
