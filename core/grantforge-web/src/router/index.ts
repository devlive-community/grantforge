// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { createRouter, createWebHashHistory } from 'vue-router'
import { useAuth } from '@/stores/auth'

const router = createRouter({ history: createWebHashHistory(), routes: [
  { path: '/auth/login', name: 'login', component: () => import('@/views/AuthView.vue'), props: { mode: 'login' } },
  { path: '/auth/register', name: 'register', component: () => import('@/views/AuthView.vue'), props: { mode: 'register' } },
  { path: '/', component: () => import('@/layouts/AppLayout.vue'), meta: { requiresAuth: true }, children: [
    { path: '', redirect: '/dashboard' },
    { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { title: '概览' } },
    { path: 'admin/users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { title: '用户管理' } },
    { path: 'admin/roles', name: 'roles', component: () => import('@/views/ResourceView.vue'), props: { kind: 'roles' }, meta: { title: '角色管理' } },
    { path: 'admin/menus', name: 'menus', component: () => import('@/views/ResourceView.vue'), props: { kind: 'menus' }, meta: { title: '菜单管理' } },
    { path: 'admin/methods', name: 'methods', component: () => import('@/views/ResourceView.vue'), props: { kind: 'methods' }, meta: { title: '请求方式' } },
    { path: 'json/pretty', name: 'json', component: () => import('@/views/JsonView.vue'), meta: { title: 'JSON 工作台' } },
    { path: 'common/403', component: () => import('@/views/ErrorView.vue'), props: { status: '403' }, meta: { title: '暂无访问权限' } },
    { path: 'common/network', component: () => import('@/views/ErrorView.vue'), props: { status: 'network' }, meta: { title: '连接失败' } },
  ] },
  { path: '/common/404', component: () => import('@/views/ErrorView.vue'), props: { status: '404' } },
  { path: '/:pathMatch(.*)*', redirect: '/common/404' },
] })
router.beforeEach(async to => {
  const auth = useAuth()
  if (to.meta.requiresAuth) {
    if (!auth.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
    try { await auth.hydrate() } catch { auth.logout(); return { name: 'login', query: { redirect: to.fullPath } } }
    if (!auth.authenticated) return { name: 'login' }
    if (!to.path.startsWith('/common/') && !auth.canVisit(to.path)) return '/common/403'
  } else if (auth.authenticated && (to.name === 'login' || to.name === 'register')) return '/dashboard'
})
router.afterEach(to => { document.title = `${String(to.meta.title || '权限工作台')} · GrantForge` })
export default router
