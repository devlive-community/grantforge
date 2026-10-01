// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { translate } from '@/i18n'
import { ApiError, request } from '@/lib/api'
import { forgetLegacyToken, readUsername, rememberUsername } from '@/lib/session'
import { menuPaths } from '@/lib/tree'
import type { components } from '@/api/schema'
import type { MenuTree } from '@/types/api'

type Me = components['schemas']['MeResponse']

/**
 * The signed-in user. The session is an HttpOnly cookie, so the console learns whether it is signed in by
 * asking the server (`GET /api/v1/me`) once per page load; sign-in and sign-out go through the API too.
 */
export const useAuth = defineStore('auth', () => {
  const me = shallowRef<Me | null>(null), username = ref(readUsername())
  const navigation = shallowRef<MenuTree[]>([]), navigationReady = ref(false), navigationError = ref('')
  let restored = false, restoring: Promise<void> | undefined
  const authenticated = computed(() => me.value !== null)
  const user = computed(() => me.value ? { name: me.value.displayName || me.value.username } : null)
  const paths = computed(() => menuPaths(navigation.value))
  async function loadNavigation() {
    try {
      navigation.value = await request<MenuTree[]>('/api/v1/role/menu') || []
      navigationReady.value = true; navigationError.value = ''
    } catch { navigationReady.value = false; navigationError.value = translate('errors.navigation') }
  }
  function signedIn(value: Me) {
    me.value = value; username.value = value.username; restored = true
    rememberUsername(value.username)
  }
  /** Asks the server for the current session once; a 401 means signed out, other failures propagate. */
  async function restore() {
    if (restored) return
    restoring ??= (async () => {
      forgetLegacyToken()
      try {
        signedIn(await request<Me>('/api/v1/me', { anonymous: true }))
        await loadNavigation()
      } catch (error) {
        if (!(error instanceof ApiError && error.status === 401)) throw error
        reset(); restored = true
      }
    })().finally(() => { restoring = undefined })
    return restoring
  }
  async function login(name: string, password: string) {
    signedIn(await request<Me>('/api/v1/auth/login', { method: 'POST', anonymous: true, body: { username: name, password } }))
    navigationReady.value = false
    await loadNavigation()
  }
  /** Forgets the user locally, for example after the server reported the session expired. */
  function reset() {
    me.value = null; navigation.value = []; navigationReady.value = false; navigationError.value = ''
  }
  async function logout() {
    try { await request<null>('/api/v1/auth/logout', { method: 'POST', anonymous: true }) } catch { /* signed out locally anyway */ }
    reset(); restored = true
  }
  function canVisit(path: string) { return path === '/dashboard' || path === '/json/pretty' || !navigationReady.value || paths.value.has(path) }
  return { me, username, user, authenticated, navigation, navigationReady, navigationError, login, logout, reset, restore, loadNavigation, canVisit }
})
