#!/usr/bin/env bash
# Copyright (c) 2026 devlive-community/grantforge
#
# Licensed under the MIT License. See the LICENSE file in the
# project root for full license text.

# JavaScript SDK (sdk/grantforge-js, @grantforge/client) entry points used by CI (and runnable locally).
#
#   sdk_js.sh install   install the locked dependencies
#   sdk_js.sh build     type check and compile to dist/
#   sdk_js.sh lint      ESLint with zero warnings allowed
#   sdk_js.sh test      Vitest unit tests
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "${ROOT}/sdk/grantforge-js"

case "${1:-}" in
  install) pnpm install --frozen-lockfile ;;
  build)   pnpm typecheck && pnpm build ;;
  lint)    pnpm lint ;;
  test)    pnpm test ;;
  *)
    echo "usage: $0 {install|build|lint|test}" >&2
    exit 2
    ;;
esac
