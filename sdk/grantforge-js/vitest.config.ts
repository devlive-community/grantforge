// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { defineConfig } from 'vitest/config'

export default defineConfig({
  test: { environment: 'jsdom', include: ['src/**/*.test.ts'], restoreMocks: true, clearMocks: true },
})
