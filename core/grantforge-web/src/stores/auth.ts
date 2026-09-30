// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { computed, ref, shallowRef } from 'vue'
import { defineStore } from 'pinia'
import { authenticate, request } from '@/lib/api'
import { clearSession, readToken, readUsername, saveSession } from '@/lib/session'
import { menuPaths } from '@/lib/tree'
import type { MenuTree, User } from '@/types/api'

export const useAuth = defineStore('auth', () => {
  const token = ref(readToken()), username = ref(readUsername())
  const user = shallowRef<User | null>(null), navigation = shallowRef<MenuTree[]>([])
  const navigationReady = ref(false), navigationError = ref('')
  let hydration: Promise<void> | undefined
  const authenticated = computed(() => Boolean(token.value))
  const paths = computed(() => menuPaths(navigation.value))
  async function loadNavigation() {
    try {
      navigation.value = await request<MenuTree[]>(`/api/v1/role/menu`, { query: { id: user.value?.id } }) || []
      navigationReady.value = true; navigationError.value = ''
    } catch { navigationReady.value = false; navigationError.value = '导航权限暂未加载，可重新获取' }
  }
  async function hydrate() {
    if (!token.value || user.value) return
    if (!hydration) hydration = (async () => {
      if (!username.value) { logout(); return }
      user.value = await request<User>(`/api/v1/user/info/${encodeURIComponent(username.value)}`)
      if (!user.value) { logout(); return }
      await loadNavigation()
    })().finally(() => { hydration = undefined })
    return hydration
  }
  async function login(name: string, password: string) {
    const value = await authenticate(name, password)
    if (typeof value !== 'string' || !value) throw new Error('登录响应缺少有效令牌')
    saveSession(value, name); token.value = value; username.value = name
    user.value = null; navigationReady.value = false
    try { await hydrate() } catch (error) { logout(); throw error }
  }
  function logout() {
    clearSession(); token.value = ''; username.value = ''; user.value = null
    navigation.value = []; navigationReady.value = false; navigationError.value = ''
  }
  function canVisit(path: string) { return path === '/dashboard' || path === '/json/pretty' || !navigationReady.value || paths.value.has(path) }
  return { token, username, user, authenticated, navigation, navigationReady, navigationError, login, logout, hydrate, loadNavigation, canVisit }
})
