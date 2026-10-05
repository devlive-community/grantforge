// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { defineConfig, devices } from '@playwright/test'

/**
 * Takes the screenshots of the documentation (docs/public/screenshots) from a real, freshly set up server with demo
 * data. script/docs/screenshots.sh builds and starts the server, then runs this configuration.
 */
export default defineConfig({
  testDir: './tests/docs',
  fullyParallel: false,
  workers: 1,
  retries: 0,
  reporter: [['list']],
  outputDir: 'test-results/docs',
  use: {
    ...devices['Desktop Chrome'],
    baseURL: process.env.GRANTFORGE_BASE_URL || 'http://127.0.0.1:19090',
    viewport: { width: 1440, height: 900 },
    deviceScaleFactor: 1,
    colorScheme: 'light',
    locale: 'zh-CN',
    timezoneId: 'Asia/Shanghai',
    // Native controls such as date inputs follow the browser's own language, not the page locale.
    launchOptions: { args: ['--lang=zh-CN'] },
  },
  projects: [{ name: 'chromium' }],
})
