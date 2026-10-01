// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { defineConfig, devices } from '@playwright/test'

/**
 * Acceptance tests against a real, packaged GrantForge server (no mocks).
 * script/ci/e2e_fullstack.sh builds and starts the server, then runs this configuration.
 */
export default defineConfig({
  testDir: './tests/fullstack',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  reporter: [['list'], ['html', { outputFolder: 'playwright-report/fullstack', open: 'never' }]],
  outputDir: 'test-results/fullstack',
  use: {
    baseURL: process.env.GRANTFORGE_BASE_URL || 'http://127.0.0.1:19080',
    trace: 'retain-on-failure',
    locale: 'zh-CN',
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
