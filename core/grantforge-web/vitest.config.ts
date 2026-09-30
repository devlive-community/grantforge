// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mergeConfig, defineConfig } from 'vitest/config'
import base from './vite.config.ts'

export default mergeConfig(base({ mode: 'test', command: 'serve' }), defineConfig({
  test: { environment: 'jsdom', include: ['src/**/*.test.ts'], restoreMocks: true, clearMocks: true },
}))
