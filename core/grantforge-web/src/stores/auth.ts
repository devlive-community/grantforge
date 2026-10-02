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
 * What the user may reach comes from `GET /api/v1/me/authorization`: the resource codes of pages and buttons
 * and the API permissions. Until it loads everything stays reachable, because the server checks every call
 * anyway; answers report the current version of the permissions, and a different one reloads them.
 */
export const useAuth = defineStore('auth', () => {
  const me = shallowRef<Me | null>(null), username = ref(readUsername())
  const authorization = shallowRef<Authorization | null>(null), authorizationError = ref('')
  let restored = false, restoring: Promise<void> | undefined
  const authenticated = computed(() => me.value !== null)
  const user = computed(() => me.value ? { name: me.value.displayName || me.value.username } : null)
  const resources = computed(() => new Set(authorization.value?.resources))
  const permissions = computed(() => new Set(authorization.value?.permissions))
  let reloading: Promise<void> | undefined
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
  /** Takes over the user as the server returned it after a change (profile, password). */
  function updated(value: Me) { me.value = value }
  /** Forgets the user locally, for example after the server reported the session expired. */
  function reset() {
    me.value = null; authorization.value = null; authorizationError.value = ''
  }
  async function logout() {
    try { await request<null>('/api/v1/auth/logout', { method: 'POST', anonymous: true }) } catch { /* signed out locally anyway */ }
    reset(); restored = true
  }
  /** Whether the user may use a console resource, such as the button `system.user.btn.create`. */
  function can(code: string) {
    return authorization.value === null || resources.value.has(code)
  }
  /** Whether the user holds an API permission, such as `system.user.update`. */
  function holds(permission: string) {
    return authorization.value === null || permissions.value.has(permission)
  }
  function canVisit(path: string) {
    const resource = pageResource(path)
    return resource === undefined || can(resource)
  }
  /** Reloads the permissions once if the server reports a version other than the loaded one. */
  function observeVersion(version: string) {
    const loaded = authorization.value
    if (!loaded || String(loaded.version) === version || reloading) return
    reloading = loadAuthorization().finally(() => { reloading = undefined })
  }
  const passwordChangeRequired = computed(() => me.value?.passwordChangeRequired === true)
  return { me, username, user, authenticated, passwordChangeRequired, authorization, authorizationError, login, logout, reset, restore,
    loadAuthorization, can, holds, canVisit, observeVersion, updated }
})
