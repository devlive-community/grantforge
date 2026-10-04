// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { existsSync, readFileSync } from 'node:fs'
import { PIDS } from './setup'

/** Stops the samples the setup started. */
export default async function teardown() {
  if (!existsSync(PIDS)) return
  for (const pid of JSON.parse(readFileSync(PIDS, 'utf8')) as (number | null)[]) {
    if (pid) {
      try {
        process.kill(pid)
      } catch {
        // Already gone.
      }
    }
  }
}
