// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { translate } from '@/i18n'
import { ApiError, request } from '@/lib/api'
import { forgetLegacyToken, readUsername, rememberUsername } from '@/lib/session'
import { pageResource } from '@/permissions/manifest'
import type { components } from '@/api/schema'

type Me = components['schemas']['MeResponse']
type Authorization = components['schemas']['AuthorizationResponse']

/**
 * The signed-in user. The session is an HttpOnly cookie, so the console learns whether it is signed in by
 * asking the server (`GET /api/v1/me`) once per page load; sign-in and sign-out go through the API too.
 * What the user may reach comes from `GET /api/v1/me/authorization`; until it loads every page stays reachable,
 * because the server checks every call anyway.
 */
export const useAuth = defineStore('auth', () => {
  const me = shallowRef<Me | null>(null), username = ref(readUsername())
  const authorization = shallowRef<Authorization | null>(null), authorizationError = ref('')
  let restored = false, restoring: Promise<void> | undefined
  const authenticated = computed(() => me.value !== null)
  const user = computed(() => me.value ? { name: me.value.displayName || me.value.username } : null)
  const resources = computed(() => new Set(authorization.value?.resources))
  async function loadAuthorization() {
    try {
      authorization.value = await request<Authorization>('/api/v1/me/authorization')
      authorizationError.value = ''
    } catch { authorization.value = null; authorizationError.value = translate('errors.navigation') }
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
        await loadAuthorization()
      } catch (error) {
        if (!(error instanceof ApiError && error.status === 401)) throw error
        reset(); restored = true
      }
    })().finally(() => { restoring = undefined })
    return restoring
  }
  async function login(name: string, password: string) {
    signedIn(await request<Me>('/api/v1/auth/login', { method: 'POST', anonymous: true, body: { username: name, password } }))
    authorization.value = null
    await loadAuthorization()
  }
  /** Forgets the user locally, for example after the server reported the session expired. */
  function reset() {
    me.value = null; authorization.value = null; authorizationError.value = ''
  }
  async function logout() {
    try { await request<null>('/api/v1/auth/logout', { method: 'POST', anonymous: true }) } catch { /* signed out locally anyway */ }
    reset(); restored = true
  }
  function canVisit(path: string) {
    const resource = pageResource(path), granted = authorization.value
    return resource === undefined || granted === null || granted.unrestricted || resources.value.has(resource)
  }
  return { me, username, user, authenticated, authorization, authorizationError, login, logout, reset, restore, loadAuthorization, canVisit }
})
