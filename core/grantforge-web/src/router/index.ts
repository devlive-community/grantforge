// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { authorizeTarget, continueAuthorization } from '@/lib/authorize'
import { createRouter, createWebHashHistory } from 'vue-router'
import { translate } from '@/i18n'
import { useAuth } from '@/stores/auth'
import { useBootstrap } from '@/stores/bootstrap'

declare module 'vue-router' {
  interface RouteMeta {
    /** Message key of the page title (titles.*). */
    titleKey?: 'titles.dashboard' | 'titles.users' | 'titles.sessions' | 'titles.identitySources' | 'titles.sod' | 'titles.accessRequests' | 'titles.accessReviews' | 'titles.requests' | 'titles.account' | 'titles.tenants' | 'titles.resources' | 'titles.apis' | 'titles.health' | 'titles.oauth' | 'titles.plugins' | 'titles.services' | 'titles.policies' | 'titles.agents' | 'titles.accessAudit' | 'titles.audit' | 'titles.roles' | 'titles.org' | 'titles.groups' | 'titles.positions' | 'titles.transfer'
      | 'titles.forbidden' | 'titles.network' | 'titles.app'
    requiresAuth?: boolean
  }
}

const router = createRouter({ history: createWebHashHistory(), routes: [
  { path: '/auth/login', name: 'login', component: () => import('@/views/AuthView.vue'), props: { mode: 'login' } },
  { path: '/auth/register', name: 'register', component: () => import('@/views/AuthView.vue'), props: { mode: 'register' } },
  { path: '/setup', name: 'setup', component: () => import('@/views/AuthView.vue'), props: { mode: 'setup' } },
  { path: '/', component: () => import('@/layouts/AppLayout.vue'), meta: { requiresAuth: true }, children: [
    { path: '', redirect: '/dashboard' },
    { path: 'dashboard', name: 'dashboard', component: () => import('@/views/DashboardView.vue'), meta: { titleKey: 'titles.dashboard' } },
    { path: 'admin/users', name: 'users', component: () => import('@/views/UsersView.vue'), meta: { titleKey: 'titles.users' } },
    { path: 'admin/org', name: 'org', component: () => import('@/views/OrgView.vue'), meta: { titleKey: 'titles.org' } },
    { path: 'admin/groups', name: 'groups', component: () => import('@/views/GroupsView.vue'), meta: { titleKey: 'titles.groups' } },
    { path: 'admin/positions', name: 'positions', component: () => import('@/views/PositionsView.vue'), meta: { titleKey: 'titles.positions' } },
    { path: 'admin/transfer', name: 'transfer', component: () => import('@/views/TransferView.vue'), meta: { titleKey: 'titles.transfer' } },
    { path: 'admin/roles', name: 'roles', component: () => import('@/views/RolesView.vue'), meta: { titleKey: 'titles.roles' } },
    { path: 'admin/sod', name: 'sod', component: () => import('@/views/SodView.vue'), meta: { titleKey: 'titles.sod' } },
    { path: 'admin/access-requests', name: 'access-requests', component: () => import('@/views/AccessApprovalsView.vue'), meta: { titleKey: 'titles.accessRequests' } },
    { path: 'admin/access-reviews', name: 'access-reviews', component: () => import('@/views/AccessReviewsView.vue'), meta: { titleKey: 'titles.accessReviews' } },
    { path: 'requests', name: 'requests', component: () => import('@/views/AccessRequestsView.vue'), meta: { titleKey: 'titles.requests' } },
    { path: 'admin/audit', name: 'audit', component: () => import('@/views/AuditView.vue'), meta: { titleKey: 'titles.audit' } },
    { path: 'admin/sessions', name: 'sessions', component: () => import('@/views/SessionsView.vue'), meta: { titleKey: 'titles.sessions' } },
    { path: 'admin/identity-sources', name: 'identity-sources', component: () => import('@/views/IdentitySourcesView.vue'), meta: { titleKey: 'titles.identitySources' } },
    { path: 'platform/apis', name: 'apis', component: () => import('@/views/ApisView.vue'), meta: { titleKey: 'titles.apis' } },
    { path: 'platform/resources', name: 'resources', component: () => import('@/views/ResourcesView.vue'), meta: { titleKey: 'titles.resources' } },
    { path: 'data/policies', name: 'policies', component: () => import('@/views/PoliciesView.vue'), meta: { titleKey: 'titles.policies' } },
    { path: 'data/access-audit', name: 'access-audit', component: () => import('@/views/AccessAuditView.vue'), meta: { titleKey: 'titles.accessAudit' } },
    { path: 'data/agents', name: 'agents', component: () => import('@/views/AgentsView.vue'), meta: { titleKey: 'titles.agents' } },
    { path: 'data/services', name: 'services', component: () => import('@/views/ServicesView.vue'), meta: { titleKey: 'titles.services' } },
    { path: 'platform/plugins', name: 'plugins', component: () => import('@/views/PluginsView.vue'), meta: { titleKey: 'titles.plugins' } },
    { path: 'platform/oauth', name: 'oauth', component: () => import('@/views/OAuthView.vue'), meta: { titleKey: 'titles.oauth' } },
    { path: 'platform/health', name: 'health', component: () => import('@/views/HealthView.vue'), meta: { titleKey: 'titles.health' } },
    { path: 'platform/tenants', name: 'tenants', component: () => import('@/views/TenantsView.vue'), meta: { titleKey: 'titles.tenants' } },
    { path: 'account', name: 'account', component: () => import('@/views/AccountView.vue'), meta: { titleKey: 'titles.account' } },
    { path: 'common/403', component: () => import('@/views/ErrorView.vue'), props: { status: '403' }, meta: { titleKey: 'titles.forbidden' } },
    { path: 'common/network', component: () => import('@/views/ErrorView.vue'), props: { status: 'network' }, meta: { titleKey: 'titles.network' } },
  ] },
  { path: '/common/404', component: () => import('@/views/ErrorView.vue'), props: { status: '404' } },
  { path: '/:pathMatch(.*)*', redirect: '/common/404' },
] })
router.beforeEach(async to => {
  // Until first-run setup is done there is nobody to sign in as, so every page leads to setup.
  const bootstrap = useBootstrap()
  await bootstrap.load()
  if (bootstrap.setupRequired) return to.name === 'setup' ? true : { name: 'setup' }
  if (to.name === 'setup' && bootstrap.loaded) return { name: 'login' }
  if (to.name === 'register' && !bootstrap.registrationEnabled) return { name: 'login' }
  const auth = useAuth()
  if (to.meta.requiresAuth) {
    // The session cookie is invisible to the console; ask the server once whether it is still valid.
    try { await auth.restore() } catch { return { name: 'login', query: { redirect: to.fullPath } } }
    if (!auth.authenticated) return { name: 'login', query: { redirect: to.fullPath } }
    // A demanded or expired password must be replaced before anything else; the server enforces it too.
    if (auth.passwordChangeRequired && to.name !== 'account') return { name: 'account' }
    if (!to.path.startsWith('/common/') && !auth.canVisit(to.path)) return '/common/403'
  } else if (to.name === 'login' || to.name === 'register') {
    try { await auth.restore() } catch { return true }
    // Already signed in: an application's sign-in continues at the authorization server straight away.
    const target = authorizeTarget(to.query.authorize)
    if (auth.authenticated && target) { continueAuthorization(target); return false }
    if (auth.authenticated) return '/dashboard'
  }
})
router.afterEach(to => { document.title = `${translate(to.meta.titleKey ?? 'titles.app')} · GrantForge` })
export default router
