// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { defineConfig, devices } from '@playwright/test'

/**
 * The sample applications against a real GrantForge server: the global setup registers them in GrantForge, starts their
 * packaged jars and gives roles data policies on the entity the shop declares at start-up. script/ci/e2e_fullstack.sh
 * runs this configuration after the full-stack suite, which completes GrantForge's setup.
 */
export default defineConfig({
  testDir: './tests/samples',
  testMatch: '*.spec.ts',
  fullyParallel: false,
  forbidOnly: !!process.env.CI,
  retries: 0,
  globalSetup: './tests/samples/setup.ts',
  globalTeardown: './tests/samples/teardown.ts',
  reporter: [['list'], ['html', { outputFolder: 'playwright-report/samples', open: 'never' }]],
  outputDir: 'test-results/samples',
  use: { trace: 'retain-on-failure', locale: 'zh-CN' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
})
