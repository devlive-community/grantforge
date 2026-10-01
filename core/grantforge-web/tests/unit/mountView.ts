// Copyright (c) 2026 devlive-community/grantforge
//
// Licensed under the MIT License. See the LICENSE file in the
// project root for full license text.

import { mount, type ComponentMountingOptions } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import type { Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'

const Empty = { template: '<div></div>' }
const paths = ['/', '/dashboard', '/admin/users', '/admin/org', '/admin/groups', '/admin/roles', '/admin/menus', '/admin/methods', '/admin/sessions', '/platform/tenants', '/account', '/json/pretty',
  '/auth/login', '/auth/register', '/setup', '/common/403', '/common/404']

/** Creates a router whose routes render nothing, so views can link and navigate in isolation. */
export function testRouter(): Router {
  return createRouter({ history: createMemoryHistory(), routes: paths.map(path => ({ path, component: Empty })) })
}

/**
 * Mounts a view with a fresh Pinia store and an in-memory router, attached to the document so
 * teleported dialogs are reachable.
 */
export async function mountView<C extends Component>(component: C, options: ComponentMountingOptions<C> = {}, at = '/') {
  const pinia = createPinia()
  setActivePinia(pinia)
  const router = testRouter()
  await router.push(at)
  const wrapper = mount(component, { attachTo: document.body, ...options, global: { plugins: [pinia, router], ...options.global } })
  return { wrapper, router }
}
