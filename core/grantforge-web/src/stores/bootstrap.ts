// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { request } from '@/lib/api'
import type { components } from '@/api/schema'

type Bootstrap = components['schemas']['BootstrapResponse']
type SignInSource = Bootstrap['signInSources'][number]

/**
 * What the server says before anyone signs in: whether first-run setup is pending, whether visitors may
 * register and which identity providers users may sign in with. Loaded once for the router, and again by the sign-in page with
 * `refresh`, since an administrator may have added a provider since; when the server cannot be reached nothing is assumed, so
 * sign-in still shows its own network error instead of a misleading setup page.
 */
export const useBootstrap = defineStore('bootstrap', () => {
  const loaded = ref(false), setupRequired = ref(false), registrationEnabled = ref(false), signInSources = shallowRef<SignInSource[]>([])
  let loading: Promise<void> | undefined
  async function load(): Promise<void> {
    if (loaded.value) return
    return ask()
  }
  /**
   * Asks again even when loaded, for the sign-in page, so a provider added or switched off while the console was open shows
   * as it is now; until the answer comes, and if it fails, what was loaded before stays.
   */
  async function refresh(): Promise<void> { return ask() }
  function ask(): Promise<void> {
    loading ??= (async () => {
      try {
        const state = await request<Bootstrap | null>('/api/v1/bootstrap', { anonymous: true })
        setupRequired.value = state?.setupRequired === true
        registrationEnabled.value = state?.registrationEnabled === true
        // An older server answers without providers.
        signInSources.value = state?.signInSources ?? []
        loaded.value = true
      } catch {
        // Retried on the next navigation.
      }
    })().finally(() => { loading = undefined })
    return loading
  }
  /** Called after setup succeeded, so the guard stops sending visitors to the setup page. */
  function setupCompleted() { setupRequired.value = false }
  return { loaded, setupRequired, registrationEnabled, signInSources, load, refresh, setupCompleted }
})
